# Spring AI Alibaba Graph 示例

这是一个无需模型 API Key 即可运行的最小 Graph 项目。它用“客服问题路由”演示 Spring AI Alibaba Graph 的核心能力：

项目使用 Java 21、Spring Boot 3.5.7、Spring AI 1.1.2 和 Spring AI Alibaba Graph 1.1.2.2。

- `StateGraph`：声明节点和边
- `OverAllState`：在节点之间传递状态
- `ReplaceStrategy` / `AppendStrategy`：控制状态字段的合并方式
- 条件边：根据分类结果选择后续节点
- `CompiledGraph`：编译并运行工作流
- `MemorySaver`：保存检查点和执行历史
- `RunnableConfig.threadId`：隔离不同会话的状态

## 流程

```text
START
  │
  ▼
normalize ──► classify
                 │
       ┌─────────┴─────────┐
       │ route=auto        │ route=manual
       ▼                   ▼
  auto_reply          manual_review
       │                   │
       └─────────┬─────────┘
                 ▼
                END
```

含有“退款、扣费、账单、发票、支付”等关键词的问题会进入人工审核，其他问题自动回复。

## 运行测试

在仓库根目录执行：

```bash
mvn -pl ai-framework/spring-ai-graph -am test
```

## 启动项目

```bash
mvn -pl ai-framework/spring-ai-graph spring-boot:run
```

默认端口为 `8084`。

## 调用 Graph

自动回复分支：

```bash
curl -X POST http://localhost:8084/api/graph/run \
  -H 'Content-Type: application/json' \
  -d '{"threadId":"user-001","question":"如何修改登录密码？"}'
```

人工审核分支：

```bash
curl -X POST http://localhost:8084/api/graph/run \
  -H 'Content-Type: application/json' \
  -d '{"threadId":"user-002","question":"我的账单被重复扣费了，想申请退款"}'
```

查看某个会话的最新状态：

```bash
curl http://localhost:8084/api/graph/state/user-002
```

查看执行过程中保存的检查点：

```bash
curl http://localhost:8084/api/graph/history/user-002
```

`MemorySaver` 只适合演示，应用重启后数据会丢失。生产环境应换成持久化的 checkpoint saver，并为 `threadId` 设计稳定、唯一的生成规则。

## 关键代码

- `SupportGraphConfiguration`：Graph 的状态策略、节点、普通边、条件边和 checkpoint 配置
- `SupportGraphController`：用 `RunnableConfig.threadId` 运行 Graph，并查询状态与历史
- `SupportGraphConfigurationTest`：验证两个路由分支和 checkpoint 历史
- `SupportGraphControllerTest`：验证运行、状态查询和历史查询 REST 接口

当前节点使用确定性 Java 逻辑，便于先学清 Graph 本身。后续可以把 `classify` 或 `auto_reply` 节点替换成 Spring AI `ChatClient` 调用，Graph 的拓扑和状态管理部分不需要改变。
