# Development Log

本文档记录 maimaiDX 后端项目中有工程价值的开发、调试和文档维护过程。记录按统一结构整理：

- 问题
- 原因
- 解决方案
- 验证结果

## 2026-06-08 修复 `score_record.rank` SQL 关键字冲突

### 问题

刷新训练建议时接口返回统一错误，服务端日志显示查询 `score_record` 时 SQL 执行失败。

### 原因

`score_record` 表中存在 `rank` 字段，`rank` 在 MySQL 8 中属于关键字。MyBatis-Plus 生成 SQL 时未对该列名进行转义，导致 SQL 语法错误。

### 解决方案

在 `ScoreRecord` 实体的 `rank` 字段上添加列名映射，将数据库列名转义为 `` `rank` ``，避免生成非法 SQL。

### 验证结果

执行 `mvnw.cmd clean test` 后自动化测试通过；重新请求训练建议刷新接口，返回成功并生成训练建议记录。

## 2026-06-08 修复能力画像刷新时间字段异常

### 问题

刷新能力画像时接口返回统一错误，批量写入 `player_ability` 失败。

### 原因

数据库拒绝写入 `last_update = null`。实体中配置了自动填充，但 MyBatis-Plus 全局填充配置没有覆盖 `lastUpdate` 字段。

### 解决方案

在 MyBatis-Plus `MetaObjectHandler` 的 `insertFill` 和 `updateFill` 中增加 `lastUpdate` 当前时间填充，使实体字段与数据库非空约束保持一致。

### 验证结果

重新编译并启动服务后，能力画像刷新返回成功，训练建议刷新和 B50 查询链路也完成回归验证。

## 2026-07-07 新增成绩快照与推荐结果核心实体

### 问题

原有成绩结构更偏向当前成绩查询，不便于保存多次导入结果，也不利于做历史对比和成长趋势分析。

### 原因

当前后端核心流程需要支持“曲库导入 -> 成绩快照 -> B50 分析 -> 推荐结果 -> 成长报告”。如果只保存当前成绩，会丢失每次导入的历史状态。

### 解决方案

新增 `ScoreSnapshot` 和 `RecommendationItem` 实体及对应 Mapper；扩展 `ScoreRecord`，增加 `snapshot_id`、`user_id`、`ra`、`is_b50`、`b50_type` 等字段；扩展 `Song` 和 `SongDifficulty`，补充新曲标记、拟合难度、谱师等字段。

### 验证结果

相关实体、Mapper 和迁移脚本完成后，后端能够按快照保存成绩，并为 B50、推荐和报告接口提供数据基础。

## 2026-07-07 新增 MVP 数据库迁移脚本

### 问题

已有数据库缺少成绩快照、推荐结果以及部分曲库和谱面字段，无法支撑新的后端核心流程。

### 原因

新增接口需要保存每次导入事件、每条成绩所属快照、B50 标记、RA 值和推荐结果，而旧表结构没有这些字段或表。

### 解决方案

新增 `database/mvp_migration.sql`，通过增量迁移方式补充：

- `song.is_new`
- `song_difficulty.fit_diff`
- `song_difficulty.charter`
- `score_snapshot`
- `score_record.snapshot_id`
- `score_record.user_id`
- `score_record.ra`
- `score_record.is_b50`
- `score_record.b50_type`
- `recommendation_item`

### 验证结果

执行迁移脚本后，曲库同步、成绩导入、B50 查询和推荐结果保存可以基于新结构运行。

## 2026-07-07 新增后端核心闭环接口

### 问题

后端缺少一组围绕成绩快照和 B50 分析的统一接口，无法形成完整的曲库、成绩、推荐和报告闭环。

### 原因

原有接口分散在不同业务模块中，缺少面向快照导入和分析的统一入口。

### 解决方案

新增 `MvpDtos`、`MvpService`、`MvpServiceImpl` 和 `MvpController`，提供以下接口：

- `GET /songs`
- `POST /songs/sync`
- `GET /charts/{songId}`
- `POST /player/import`
- `GET /player/{userId}/b50`
- `POST /player/{userId}/recommend`
- `GET /player/{userId}/recommendations`
- `GET /player/{userId}/report`

同时新增默认曲库数据 `backend/src/main/resources/data/songs-mvp.json` 和示例成绩数据 `database/sample_score_import.json`。

### 验证结果

执行 `./mvnw.cmd test`，当时已有自动化测试通过；核心接口可以基于本地 JSON 数据完成曲库同步、成绩导入、B50 查询和推荐生成。

## 2026-07-08 修复 `/songs/sync` 空 POST 请求问题

### 问题

调用 `POST /api/songs/sync` 时，如果请求体为空，接口返回服务端错误。

### 原因

原接口方法要求 `@RequestBody`，而 PowerShell 空 POST 默认不会发送 JSON 请求体，导致 Spring 无法按 JSON RequestBody 解析。

### 解决方案

将默认曲库同步接口改为无请求体：

- `POST /songs/sync`：读取默认本地 JSON 数据并同步曲库。
- `POST /songs/sync/custom`：接收请求体 JSON，同步自定义曲库。

### 验证结果

执行 `./mvnw.cmd test`，自动化测试通过；再次调用 `POST /api/songs/sync` 可以正常同步默认曲库。

## 2026-07-13 记录曲库导入去重与唯一约束设计

### 问题

曲库同步接口支持重复执行，如果没有去重策略，重复导入可能产生重复歌曲或重复谱面。

### 原因

同步逻辑采用“先查询再插入或更新”的方式。如果数据库层缺少唯一约束，并发或重复数据导入时仍可能造成重复记录。

### 解决方案

在文档中明确当前策略：

- `song.song_id` 用于标识唯一歌曲。
- `song_difficulty(song_id, difficulty)` 用于标识唯一谱面。
- 应用层先查询再插入或更新，数据库唯一约束作为兜底。

### 验证结果

README 中补充了去重设计说明，避免将重复同步误解为追加多份相同曲库数据。

## 2026-07-14 记录 B50 DTO 转换中的 N+1 查询优化点

### 问题

`toScoreItem` 在转换 B50 成绩时，会针对每条成绩分别查询歌曲和谱面信息。

### 原因

当前实现为了保持代码直观，在循环中通过 `songRepository.selectById` 和 `songDifficultyRepository.selectById` 获取关联数据。B50 最多 50 条，数据量较小时可以接受，但数据规模扩大后会产生典型 N+1 查询问题。

### 解决方案

暂不改动业务代码，将该问题记录为后续优化点：先批量收集 `songId` 和 `difficultyId`，使用批量查询加载歌曲和谱面，再构建 Map 供 DTO 转换使用。

### 验证结果

该记录已写入项目文档的优化方向，用于后续性能优化时参考。

## 2026-07-14 新增成长趋势对比接口

### 问题

系统已有成绩快照和 B50 查询能力，但缺少对比两次快照变化的接口。

### 原因

玩家成长分析需要展示两次导入之间的 rating 变化、B50 边缘 RA 变化，以及 B50 曲目进入、掉出和提升情况。

### 解决方案

新增 `GET /api/player/{userId}/growth?fromSnapshotId=1&toSnapshotId=2`。

实现方式：

- 在 `MvpService` 中新增 `getGrowth`。
- 在 `MvpServiceImpl` 中复用已有 `getB50(userId, snapshotId)`。
- 使用 `songId + difficulty` 作为当前版本的谱面对比 key。
- 返回 `ratingDelta`、`edgeRaDelta`、`newB50`、`droppedB50`、`improvedScores`。
- 新增 `GrowthResponse` 和 `GrowthScoreChangeItem` DTO。

### 验证结果

新增 `MvpControllerMockTest#getGrowthComparesTwoSnapshots` 覆盖接口路由和关键字段。执行 `./mvnw.cmd test`，自动化测试通过；实际请求 `GET /api/player/999/growth?fromSnapshotId=1&toSnapshotId=2` 返回 `code=200`。

## 2026-07-15 完善统一异常处理

### 问题

调用 `GET /api/player/999/growth?fromSnapshotId=999&toSnapshotId=3` 时，如果快照不存在，接口返回 HTTP 500。

### 原因

`getB50` 在找不到成绩快照时抛出 `IllegalArgumentException`，但全局异常处理器没有单独处理该异常，导致它落入未知异常分支。

### 解决方案

完善 `GlobalExceptionHandler`：

- `IllegalArgumentException` 返回 HTTP 400 和统一 `Result`。
- 未知异常返回 HTTP 500 和固定错误信息。
- `Result.data` 添加 `@JsonInclude(JsonInclude.Include.ALWAYS)`，确保错误响应中稳定输出 `data: null`。
- 在 `MvpControllerMockTest` 中挂载 `GlobalExceptionHandler`，补充快照不存在的异常路径测试。

### 验证结果

执行 `./mvnw.cmd clean test`，自动化测试通过。实际请求不存在快照时，返回：

```json
{
  "code": 400,
  "message": "未找到成绩快照，请先导入成绩 JSON",
  "data": null
}
```

## 2026-07-16 生成公开版 README 初稿

### 问题

原 README 包含历史描述和乱码内容，不适合作为公开仓库说明。

### 原因

项目经历过多轮功能调整，早期文档中包含一些不属于当前后端核心闭环的内容。继续保留这些内容会影响项目说明的准确性。

### 解决方案

重写根目录 `README.md`，聚焦当前已实现的后端主线：

- 曲库同步
- 歌曲查询
- 成绩导入
- 成绩快照
- B50 分析
- 规则推荐
- 成长报告
- growth 成长趋势对比
- 统一异常处理

同时将数据库账号、密码、密钥等敏感配置改为占位符。

### 验证结果

逐项检查 README 中列出的接口和核心表，确认均存在于当前代码或 SQL 脚本中；扫描后未发现敏感配置或未实现能力描述。

## 2026-07-16 README 去风险化精修

### 问题

README 中仍存在一些容易让读者误解为当前核心能力的依赖或配置描述。

### 原因

部分依赖和配置存在于项目中，但没有参与当前后端核心闭环。如果将它们写入技术栈或核心能力，会降低文档准确性。

### 解决方案

对 README 做小范围文案精修：

- 技术栈仅保留当前主线实际使用的内容。
- 删除未参与当前主线的依赖描述。
- 将 Swagger 地址调整为优先使用 `/api/swagger-ui/index.html`。
- 保留 `/api/swagger-ui.html` 作为备选。
- 删除职业场景类表述，使 README 更接近普通 GitHub 项目说明。

### 验证结果

扫描 README，确认不包含未落地能力的宣传词，不包含真实数据库密码或密钥；Java 版本、SQL 脚本名和 Swagger 地址均与当前项目一致。

## 2026-07-16 整理公开仓库结构与配置

### 问题

原 Git 仓库位于 `backend` 目录，根目录下的前端、数据库脚本、项目文档和 README 未被统一纳入版本控制；部分本地配置仍直接写在 Spring 配置文件中。

### 原因

项目最初从后端模块开始建立仓库，后续新增的模块位于仓库根目录之外。同时，开发环境配置沿用了本机连接参数，不适合直接进入公开仓库。

### 解决方案

- 将 Git 仓库根目录调整为项目根目录，统一管理 `backend`、`frontend`、`database`、`docs` 和 `README.md`。
- 新增根目录 `.gitignore`，排除 Maven 构建产物、前端依赖、前端构建产物、IDE 配置、日志和本地配置文件。
- 将数据库密码和其他密钥字段替换为环境变量占位符。
- 使用 GitHub noreply 邮箱作为当前仓库的提交身份，避免在公开提交中暴露个人邮箱。

### 验证结果

- 待提交内容共 155 个文件，未包含 `node_modules`、`target`、`dist`、本地配置和大文件。
- 后端执行 `mvnw.cmd clean test`，9 个测试全部通过。
- 前端执行 `npm run type-check`，类型检查通过。
- 前端执行 `npm run build:h5`，H5 构建成功。
