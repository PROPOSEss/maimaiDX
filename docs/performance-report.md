# 性能验证与可复现压测报告

生成时间：2026-08-04

## 1. 测试环境

本次压测只连接本地开发环境，没有访问线上服务或第三方接口。

| 项目 | 信息 |
| --- | --- |
| 操作系统 | Microsoft Windows 11 家庭版 中文版 10.0.26200，64 位 |
| CPU | 13th Gen Intel(R) Core(TM) i5-1340P，12 核，16 逻辑处理器 |
| 内存 | 约 16 GB |
| Java | OpenJDK 21.0.9 Temurin |
| MySQL | 8.0.43，本地库 `maimai_dx` |
| Redis | 本机 Windows Redis 服务，应用实际连接 `127.0.0.1:6379` |
| RabbitMQ | Docker 容器 `maidx-rabbitmq` |
| 后端地址 | `http://localhost:8080/api` |

说明：本机同时存在 Docker Redis 容器 `maidx-redis`。本次排查确认后端实际连接的是宿主机 `127.0.0.1:6379` 上的 Windows Redis 服务，因此 Redis key 验证使用宿主机 `redis-cli`，不是 `docker exec maidx-redis redis-cli`。

## 2. 压测工具与局限

本次使用项目中的 PowerShell 7 脚本 `performance/run-load.ps1` 做本地可复现压测。脚本支持配置 baseUrl、userId、并发数、持续时间和导入记录数。

局限：

- PowerShell 脚本适合本地功能与趋势验证，不等同于专业压测平台。
- 单机本地结果不能外推到生产环境。
- 本报告不描述高并发能力、容量上限或线上性能。

## 3. 测试数据

使用 `performance/seed-performance-data.ps1` 生成本地合成成绩数据，使用 `performance/cleanup-performance-data.ps1` 清理。数据库密码从 `DB_PASSWORD` 环境变量读取，不写入脚本或报告。

本轮修正后，Assistant 压测使用专用用户 `990001`，不污染演示用户 `999`：

| 目标生成量 | `score_record` 总数 | `user_id=990001` 记录数 |
| ---: | ---: | ---: |
| 10000 | 10051 | 10000 |
| 50000 | 50051 | 50000 |
| 100000 | 100051 | 100000 |

## 4. 场景 A：曲目谱面查询与 Redis

接口：

```http
GET /api/charts/mvp001
```

真实调用链：

```text
MvpController#getCharts
-> MvpServiceImpl#getCharts
-> SongCatalogServiceImpl#getCharts
-> SongRepository / SongDifficultyRepository
```

`SongCatalogServiceImpl#getCharts` 是 `public` 方法，并通过 Spring Bean 调用，方法上使用 `@Cacheable(cacheNames = "songCharts", key = "#songId")`。项目启用了 `@EnableCaching`，当前运行时 CacheManager 为 RedisCacheManager。

### 4.1 Redis key 验证

执行前清理宿主机 Redis：

```powershell
redis-cli FLUSHDB
```

第一次请求后：

| 项目 | 结果 |
| --- | --- |
| HTTP 状态 | 200 |
| 宿主机 Redis key | `maidx:songCharts::mvp001` |
| TTL | 21600 秒 |
| Docker 容器内部 `maidx:*` key | 空 |

结论：之前未观察到 key 的原因不是接口未缓存，而是查看了错误的 Redis 实例。后端实际写入宿主机 Redis，`docker exec maidx-redis redis-cli` 看到的是另一个 Redis 实例。

### 4.2 SQL 访问验证

使用 MySQL `general_log` 排除统计 SQL 自身后计数：

| 操作 | 歌曲相关 SQL 次数 | 结论 |
| --- | ---: | --- |
| key 存在后再次请求 | 0 | 命中 Redis，没有访问歌曲表 |
| 删除 `maidx:songCharts::mvp001` 后请求 | 2 | 回源 MySQL，并重新写入缓存 |

### 4.3 冷缓存首次请求

每次请求前删除目标 key，独立执行 10 次：

| 指标 | 结果 |
| --- | ---: |
| 请求数 | 10 |
| 失败率 | 0 |
| 平均值 | 39.25 ms |
| 中位数 | 19.32 ms |
| 单次耗时 | 221.44, 19.68, 16.83, 19.82, 21.38, 22.71, 15.66, 18.82, 18.96, 17.18 ms |

第一条请求明显偏高，包含连接和运行时预热影响，因此不计算单次请求吞吐量。

### 4.4 热缓存持续压测

执行前先请求一次预热，并确认 key 存在且 TTL 有效。

参数：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\performance\run-load.ps1 `
  -Scenario charts -Vus 5 -DurationSeconds 20 -SongId mvp001
```

结果：

| 请求数 | 成功数 | 失败率 | 吞吐量 | 平均 | P50 | P95 | P99 | 歌曲相关 SQL 次数 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 12084 | 12084 | 0 | 602.68 req/s | 7.99 ms | 7.64 ms | 12.38 ms | 15.83 ms | 0 |

结论：热缓存期间没有重复访问 MySQL 歌曲表。

### 4.5 Redis 不可用时降级

由于当前终端没有停止 Windows Redis 服务的权限，本轮通过临时以 `REDIS_PORT=6390` 启动后端模拟 Redis 不可用。

参数同热缓存场景：

| 请求数 | 成功数 | 失败率 | 吞吐量 | 平均 | P50 | P95 | P99 | 歌曲相关 SQL 次数 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3140 | 3140 | 0 | 156.77 req/s | 31.33 ms | 21.83 ms | 62.36 ms | 84.58 ms | 6280 |

日志中出现 `RedisConnectionFailureException`，但接口保持 HTTP 200，并回退到 MySQL 查询。

## 5. 场景 B：最近成绩查询

接口：

```http
POST /api/assistant/query
X-User-Id: 990001
Content-Type: application/json; charset=utf-8

{"message":"查看我最近20条成绩"}
```

参数：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\performance\run-load.ps1 `
  -Scenario assistant -UserId 990001 -Vus 5 -DurationSeconds 20
```

结果：

| `user_id=990001` 记录数 | 请求数 | 成功数 | 失败率 | 吞吐量 | 平均 | P50 | P95 | P99 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10000 | 5331 | 5331 | 0 | 266.17 req/s | 18.07 ms | 15.94 ms | 22.76 ms | 30.55 ms |
| 50000 | 6041 | 6041 | 0 | 301.54 req/s | 15.99 ms | 15.21 ms | 21.79 ms | 25.92 ms |
| 100000 | 5702 | 5702 | 0 | 284.56 req/s | 16.93 ms | 16.19 ms | 22.89 ms | 27.16 ms |

EXPLAIN：

```sql
EXPLAIN
SELECT *
FROM score_record
WHERE user_id = 990001
ORDER BY last_play_time DESC,
         best_play_time DESC,
         created_at DESC
LIMIT 20;
```

| 记录数 | type | possible_keys | key | key_len | rows | filtered | Extra |
| ---: | --- | --- | --- | ---: | ---: | ---: | --- |
| 10000 | ref | idx_score_user_b50, idx_score_user_recent_time | idx_score_user_recent_time | 9 | 5025 | 100.00 | NULL |
| 50000 | ref | idx_score_user_b50, idx_score_user_recent_time | idx_score_user_recent_time | 9 | 25000 | 100.00 | NULL |
| 100000 | ref | idx_score_user_b50, idx_score_user_recent_time | idx_score_user_recent_time | 9 | 49711 | 100.00 | NULL |

结论：

- 查询稳定使用 `idx_score_user_recent_time`。
- `Extra` 未出现 `Using filesort`。
- 本结论只针对“最近成绩查询”这个 SQL，不代表 TOP_SCORES 的 Java 层全量去重已经优化。

## 6. 场景 C：同步与异步导入对比

接口：

```http
POST /api/player/import?userId=999
POST /api/player/import/async?userId=999
```

每个记录规模执行 30 次，异步请求每次使用唯一 `requestId`。

### 6.1 接口响应时间

| 导入记录数 | 模式 | 请求数 | 成功数 | 失败率 | 平均返回时间 | P50 | P95 | P99 |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20 | 同步 | 30 | 30 | 0 | 162.10 ms | 154.12 ms | 200.75 ms | 547.46 ms |
| 20 | 异步提交 | 30 | 30 | 0 | 30.29 ms | 20.31 ms | 37.97 ms | 306.55 ms |
| 50 | 同步 | 30 | 30 | 0 | 332.71 ms | 333.45 ms | 381.41 ms | 503.45 ms |
| 50 | 异步提交 | 30 | 30 | 0 | 25.68 ms | 20.37 ms | 31.65 ms | 167.90 ms |
| 100 | 同步 | 30 | 30 | 0 | 643.77 ms | 641.86 ms | 754.92 ms | 760.63 ms |
| 100 | 异步提交 | 30 | 30 | 0 | 26.52 ms | 20.76 ms | 33.38 ms | 162.70 ms |

### 6.2 异步总处理时间

脚本轮询观察值：

| 导入记录数 | 异步总处理平均值 | 异步总处理 P95 |
| ---: | ---: | ---: |
| 20 | 547.28 ms | 549.20 ms |
| 50 | 543.32 ms | 555.52 ms |
| 100 | 1061.35 ms | 1070.59 ms |

`import_task.created_at` 到 `finished_at` 的数据库统计值：

| 导入记录数 | SUCCESS 数 | 平均值 | P50 | P95 |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 30 | 133.33 ms | 0 ms | 1000 ms |
| 50 | 30 | 400.00 ms | 0 ms | 1000 ms |
| 100 | 30 | 633.33 ms | 1000 ms | 1000 ms |

说明：当前 `import_task` 时间字段为秒级精度，数据库统计会出现 0ms 或 1000ms 的粗粒度结果。后续如果要更准确比较异步总耗时，应将任务阶段时间记录为毫秒级，或增加消费开始、写入完成等阶段耗时字段。

结论：

- 异步接口提交返回更快，但不代表数据库写入总处理时间更短。
- 异步方案的价值是快速响应、削峰和任务状态可追踪。
- 本轮未修改 RabbitMQ 流程。

## 7. 补充索引验证：定数范围查询

SQL：

```sql
EXPLAIN
SELECT *
FROM song_difficulty
WHERE is_deleted = 0
  AND level_decimal >= 13.0
  AND level_decimal <= 14.5
  AND level_decimal IS NOT NULL;
```

结果：

| type | possible_keys | key | rows | filtered | Extra |
| --- | --- | --- | ---: | ---: | --- |
| ALL | NULL | NULL | 90 | 1.11 | Using where |

数据量：

| 项目 | 数量 |
| --- | ---: |
| `song_difficulty` 总数 | 99 |
| `13.0` 到 `14.5` 匹配数 | 52 |

结论：当前曲库规模很小，优化器选择全表扫描。本轮按要求没有创建或修改索引。

## 8. RabbitMQ 队列检查

导入压测后执行：

```powershell
docker exec maidx-rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
```

| 队列 | messages | ready | unacked |
| --- | ---: | ---: | ---: |
| `maidx.score.import.queue` | 0 | 0 | 0 |
| `maidx.score.import.dlq` | 0 | 0 | 0 |

## 9. 不能得出的结论

本报告不能说明：

- 系统具备生产环境承载能力。
- 系统适合百万级用户或大规模线上压测。
- RabbitMQ 让单次数据库写入更快。
- PowerShell 压测结果等价于 k6、JMeter 或专业压测平台结果。

## 10. 后续优化建议

1. 统一本地 Redis 使用方式，避免 Windows Redis 服务与 Docker Redis 容器同时存在导致验收看错实例。
2. 为 `performance/run-load.ps1` 增加结果文件落盘，方便多轮横向对比。
3. 引入 k6 或 JMeter 复测核心场景。
4. 为异步导入增加毫秒级阶段耗时记录，提升总处理时间统计可信度。
5. TOP_SCORES 后续可从 Java 层全量去重演进为 SQL 窗口函数、分组子查询或最佳成绩汇总表。

## 11. 数据清理结果

执行：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\performance\cleanup-performance-data.ps1
redis-cli FLUSHDB
```

清理后校验：

| 项目 | 结果 |
| --- | ---: |
| `score_record` 总数 | 51 |
| perf snapshot 数 | 0 |
| perf import_task 数 | 0 |
| `maidx.score.import.queue` 积压 | 0 |
| `maidx.score.import.dlq` 积压 | 0 |

## 12. 自动化测试

执行：

```powershell
.\mvnw.cmd test
```

结果：

| 指标 | 结果 |
| --- | ---: |
| Tests run | 78 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | SUCCESS |
