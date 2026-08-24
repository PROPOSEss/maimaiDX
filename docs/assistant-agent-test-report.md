# Assistant Agent 改造测试报告

## 1. 结论

本次 Agent 化改造通过完整 Maven 测试：

```text
Tests run: 97
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

其中 Assistant 模块相关测试 42 项，覆盖严格 JSON 规划、规则降级、可信用户身份、Tool Registry、Schema 校验、单 Tool 限制、五个 Tool、业务 Service 和响应结构。

## 2. 测试信息

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-08-24 |
| 时区 | Asia/Shanghai |
| 基线提交 | `e73c497` 加当前工作区 Agent 改造 |
| Java | OpenJDK 17.0.20 |
| Maven | 3.9.16 |
| 系统编码 | UTF-8 |
| 测试命令 | `mvn clean test -q` |

执行 `clean` 后重新运行，确保统计不包含已替换测试留下的历史 Surefire 报告。

## 3. Assistant 测试套件

| 测试类 | 数量 | 结果 | 主要覆盖 |
| --- | ---: | --- | --- |
| `LlmAssistantAgentPlannerTest` | 3 | 通过 | 严格 JSON、身份参数注入拦截、LLM 关闭时规则降级 |
| `AssistantUserContextResolverTest` | 2 | 通过 | 开发 Header/默认用户、生产 JWT 与 Header 隔离 |
| `AssistantChineseEncodingIntegrationTest` | 1 | 通过 | 中文 answer 与 structuredData UTF-8 输出 |
| `AssistantControllerMockTest` | 2 | 通过 | Controller 编排、tool 与 structuredData 响应字段 |
| `OpenAiCompatibleLlmClientTest` | 7 | 通过 | LLM 开关、HTTP 调用、超时及异常响应 |
| `LlmIntentParserTest` | 7 | 通过 | 旧解析兼容层与异常降级回归 |
| `RuleBasedIntentParserTest` | 6 | 通过 | 最近、最高、推荐、训练等规则解析回归 |
| `AssistantAgentServiceImplTest` | 4 | 通过 | 单 Tool 执行、UNKNOWN、多 Tool 拒绝、用户校验 |
| `AssistantBusinessServiceImplTest` | 5 | 通过 | 最近成绩、Top 去重、随机推荐、训练建议、歌曲搜索 |
| `AssistantToolRegistryTest` | 2 | 通过 | Schema 先校验后执行、重复 Tool 名称拒绝 |
| `AssistantToolsTest` | 3 | 通过 | 五个 Tool 的 Service 委托和结构化转换 |
| 合计 | 42 | 全部通过 | — |

## 4. 核心验收项

### 4.1 单轮单 Tool

- 内部 Plan 使用 ToolCall 列表，为未来扩展保留数据结构；
- 当前执行器硬限制最多一个 Tool；
- 两个 ToolCall 的 Plan 会在执行前被拒绝；
- 不存在第二个 Tool 被静默忽略的路径。

### 4.2 严格 JSON 规划

已验证合法输入：

```json
{
  "toolName": "recent_scores",
  "arguments": {
    "limit": 5
  }
}
```

以下情况会拒绝 LLM 结果并降级：

- 非 JSON；
- 根节点不是对象；
- 出现未知根字段；
- Tool 名称不存在；
- `arguments` 不是对象；
- 参数类型或范围错误；
- 出现 `userId` 等未声明参数。

### 4.3 UserContext 安全

- 开发环境可读取 `X-User-Id`；
- 开发 Header 缺失时使用演示默认用户；
- `prod` Profile 忽略开发 Header；
- 生产环境必须提供有效 Bearer JWT；
- Tool 方法单独接收 `AssistantUserContext`，参数 Map 不包含可信身份。

### 4.4 五个 Tool

| Tool | 验收结果 |
| --- | --- |
| 最近成绩 | 验证默认数量、Service 调用、谱面/歌曲关联结果 |
| 最高成绩 | 验证同谱面去重和达成率排序 |
| 随机推荐 | 验证同歌曲不重复和定数范围业务入口 |
| 训练建议 | 验证后端确定性建议、数量限制和 evidence |
| 歌曲搜索 | 验证关键词、分页上限和 SongItem 转换 |

### 4.5 响应兼容

新响应字段已验证：

- `answer`
- `tool.name`
- `tool.plannerSource`
- `tool.success`
- `tool.arguments`
- `structuredData`

旧顶层结构化字段仍由 Agent Service 同步赋值，避免客户端立即迁移。

### 4.6 隐私与 Answer

- 当前唯一 `AssistantAnswerGenerator` 实现为确定性实现；
- Answer Generator 不依赖 `LlmClient`；
- ToolResult、成绩、训练 evidence 和 structuredData 不会进入外部总结请求；
- LLM Summary 仅保留 Java 接口扩展点，没有外部实现或启用开关；
- LLM 默认关闭，因此默认运行模式下用户自然语言也不会出站。

## 5. 全项目回归

除 Assistant 的 42 项测试外，其余 55 项测试覆盖原有业务，包括：

- 成绩导入与请求幂等；
- RabbitMQ 发送失败、重试、消费者 ACK/DLQ；
- Redis 缓存读取/写入失败降级；
- 曲库同步与缓存清理；
- Controller 和统一异常响应；
- MVP 推荐、成长报告等原有业务。

全部测试保持通过，说明 Assistant 改造未破坏现有回归集。

## 6. 本报告不代表的范围

本轮测试没有声明以下结论：

- 未连接真实外部 LLM 做在线验收；
- 未向任何外部 Summary 服务发送用户数据；
- 未使用真实生产 JWT 网关或 Spring Security Context；
- 未执行真实 MySQL、Redis、RabbitMQ 的 Testcontainers 集成测试；
- 未进行 Agent API 性能或并发压测；
- 未实现或测试多 Tool 执行和多轮 Memory。

这些内容属于后续独立验收范围，不影响当前单轮单 Tool Agent 的单元与接口回归结论。

## 7. 后续建议

1. 引入 Testcontainers，对五个 Tool 的真实 SQL 和分页行为做集成验证；
2. 将生产身份解析迁移到统一 Spring Security Context；
3. 为 Tool 执行增加耗时、失败码和 traceId 指标；
4. 若未来启用本地 LLM Summary，增加目标地址白名单与“零敏感字段出站”契约测试；
5. 多 Tool 扩展前先定义部分成功、调用顺序和结构化结果合并协议。
