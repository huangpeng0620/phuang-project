# Spring AI Alibaba Graph：客服邮件处理工作流

这个模块按 [Spring AI Alibaba Graph 工作流编排指南](https://java2ai.com/docs/frameworks/graph-core/quick-start/) 中的客服邮件场景实现：读取邮件、判断意图、查询文档或创建缺陷工单、生成回复、必要时人工审核，最后记录发送结果。

示例重点是 Graph 的状态流转和中断恢复。邮件分类与回复草稿通过 Spring AI 的 ChatClient 调用 DashScope；知识库检索、缺陷工单和邮件发送目前是明确标注的 mock，不会连接真实业务系统，也不会真的发邮件。

## 工作流总览

    START
      └─ read_email
           └─ classify_intent
                ├─ question / feature ── search_documentation ─┐
                ├─ bug ──────────────── bug_tracking ─────────┤
                ├─ other ─────────────────────────────────────┤
                │                                              ▼
                │                                       draft_response
                │                                         ├─ high / critical / complex ── human_review
                │                                         └─ 普通邮件 ───────────────────── send_reply
                └─ billing / critical ── human_review

    human_review ── 审核通过 ── send_reply ── END
          └──────── 审核拒绝 ─────────────── END

账单类或 critical 邮件依照网页示例可在分类后直接进入人工审核，此时还没有回复草稿；审核人通过时需要一并提交回复正文。其他分支会先完成分类、检索和草拟，再按风险决定是否需要人工审核。

## Graph 核心概念在本例中的对应

| Graph 概念 | 示例中的作用 |
| --- | --- |
| StateGraph | 在 SupportGraphConfiguration 中声明节点、普通边和条件边。 |
| OverAllState | 跨节点共享邮件原文、分类结果、客户历史、检索内容、草稿和审核结果。每个 NodeAction 返回一个 Map 作为本节点的状态增量。 |
| 状态合并策略 | 普通字段用 ReplaceStrategy 覆盖；messages 用 AppendStrategy 累积，方便查看节点执行记录。 |
| 条件边 | classify_intent 和 draft_response 节点把目标节点写入 next_node，Graph 根据该值选择下一节点。 |
| CompiledGraph | 将声明式的 StateGraph 编译成可运行工作流，由 Controller 启动、查询和恢复。 |
| RunnableConfig.threadId | 作为一个邮件会话的 checkpoint 标识；启动、查看状态和恢复审核必须使用同一个值。 |
| MemorySaver | 保存状态快照与执行历史，供审核暂停后继续运行；仅保存在内存，进程重启后会丢失。 |
| interruptBefore | 配置在进入 human_review 之前暂停，让外部审核人先看到当前状态，再提交审核决定。 |

### 节点说明

- read_email：读取邮件输入并初始化处理状态；当前附加的是固定的 mock 客户历史。
- classify_intent：调用模型输出结构化分类，示例类别包括 question、bug、billing、feature、complex、other；紧急程度包括 low、medium、high、critical。
- search_documentation：根据邮件内容返回几条固定的示例文档。真实项目可替换成向量检索或企业知识库。
- bug_tracking：生成演示工单编号。真实项目可在这里调用 Jira、禅道或内部工单 API。
- draft_response：把分类、邮件和检索/工单上下文交给模型，生成待发送草稿。
- human_review：读取审核人的 approved 和 editedResponse；拒绝时流程结束，通过时进入发送节点。
- send_reply：记录最终回复并把状态标记为 SENT。此节点只模拟发送，不会发送真实邮件。

## 运行环境

- Java 21
- Maven
- 一个可用的 DashScope API Key
- 本模块依赖 Spring Boot 3.5.7、Spring AI 1.1.2、Spring AI Alibaba Graph Core 1.1.2.2 和 DashScope Starter 1.1.2.1。

先在终端设置 API Key（不要把真实 Key 提交到源码或 README）：

    export DASHSCOPE_API_KEY='替换成你自己的 DashScope API Key'

在仓库根目录启动：

    mvn -pl ai-framework/spring-ai-graph spring-boot:run

服务默认监听 http://localhost:8084。模型调用使用 application.yml 配置的 qwen-plus；如果你的账号不可用该模型，可修改模型名。

## HTTP 接口

### 启动工作流

POST /api/graph/run

    curl -X POST http://localhost:8084/api/graph/run \
      -H 'Content-Type: application/json' \
      -d '{
        "threadId": "mail-1001",
        "emailId": "email-1001",
        "senderEmail": "alice@example.com",
        "emailContent": "你好，我忘记了登录密码，请问如何重置？"
      }'

响应包含 node、nextNode 和共享状态 state。若 nextNode 是 human_review，表示 Graph 已在审核节点前暂停；普通邮件通常会走完发送分支。具体分类由模型决定，因此请以实际响应为准。

### 查看当前状态和执行历史

    curl http://localhost:8084/api/graph/state/mail-1001
    curl http://localhost:8084/api/graph/history/mail-1001

state 中可以看到 classification、search_results、draft_response、messages 等字段。历史接口返回这个 threadId 保存的各个 checkpoint。

### 提交人工审核并恢复工作流

先用一封账单邮件演示“分类后直接审核”的路径：

    curl -X POST http://localhost:8084/api/graph/run \
      -H 'Content-Type: application/json' \
      -d '{
        "threadId": "mail-1002",
        "emailId": "email-1002",
        "senderEmail": "bob@example.com",
        "emailContent": "我的订单被重复扣费了，请帮我核实并退款。"
      }'

查看 mail-1002 状态确认其等待人工审核，然后审核通过并提供要发送的正文：

    curl -X POST http://localhost:8084/api/graph/mail-1002/review \
      -H 'Content-Type: application/json' \
      -d '{
        "approved": true,
        "editedResponse": "您好，我们已收到重复扣费反馈。请回复订单号，我们会为您核对账单并跟进退款申请。"
      }'

如果一个高风险邮件已生成草稿，审核时可以不传 editedResponse，系统会发送原草稿；传入 editedResponse 则会以审核人修改后的内容为准。将 approved 设为 false 会记录拒绝结果并结束流程，不会进入发送节点。

## 如何理解暂停与恢复

1. /run 使用 RunnableConfig.threadId 启动 Graph；每个节点只返回它要更新的字段。
2. Graph 执行到 human_review 前，由 CompileConfig.interruptBefore(...) 暂停并保存 checkpoint。
3. 审核接口使用同一个 threadId 读取状态，并调用 updateState(...) 把审核结果合并到 checkpoint。
4. 接着用更新后的 RunnableConfig 调用 stream(null, config)，从暂停处继续，而不是重新跑分类和检索。

Controller 中的 source=rest-api 是 RunnableConfig 的 metadata，用来携带本次运行的附加上下文；它和跨节点传递的 Graph state 是两回事。

## 代码位置

- src/main/java/com/phuang/graph/config/SupportGraphConfiguration.java：Graph 拓扑、状态合并策略、MemorySaver 和中断配置。
- src/main/java/com/phuang/graph/workflow/EmailWorkflowNodes.java：各节点的分类、检索、工单、草稿、审核和发送逻辑。
- src/main/java/com/phuang/graph/workflow/EmailWorkflowState.java：共享状态字段及节点名称。
- src/main/java/com/phuang/graph/model/EmailClassification.java：模型分类结果的数据结构。
- src/main/java/com/phuang/graph/controller/SupportGraphController.java：REST 启动、状态查询、历史查询和审核恢复接口。
- src/main/resources/application.yml：服务端口、DashScope 环境变量和模型配置。

## 与真实业务集成时要替换的部分

1. 将 searchDocumentation 中的固定文本替换为真实文档检索。
2. 将 readEmail 中的固定客户历史替换为客户/订单系统查询。
3. 将 bugTracking 中的随机编号替换为工单系统调用，并设计失败重试与幂等策略。
4. 将 sendReply 替换为邮件服务调用；真实发送前应校验收件人、审核状态和重复发送风险。
5. 将 MemorySaver 替换为持久化 checkpoint saver，否则应用重启后不能恢复审核中的邮件。
6. 为 threadId 设计稳定且唯一的生成方式，并增加身份验证和权限检查，避免未授权用户读取或审批邮件。
