package com.phuang.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description 工具挂起与外部结果回填示例
 * @author huangpeng
 * @since 2026/11/19
 */
@RestController
@RequestMapping("tool/suspend")
public class ToolSuspendTestController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("/chat")
    public Msg chat(@RequestParam(value = "orderId", defaultValue = "A100") String orderId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerSchema(ToolSchema.builder()
                .name("query_order")
                .description("从外部订单系统查询订单")
                .parameters(Map.of("type", "object",
                        "properties", Map.of("orderId", Map.of("type", "string")), "required", List.of("orderId")))
                .build());

        HarnessAgent agent = HarnessAgent.builder()
                        .name("order-agent")
                        .sysPrompt("查询订单时必须调用 query_order 工具；收到工具结果后直接回答，不要重复调用工具。")
                        .model(qianwenChatModel)
                        .toolkit(toolkit)
                        .build();

        RuntimeContext context = RuntimeContext.builder()
                        .userId("user-001")
                        .sessionId(UUID.randomUUID().toString())
                        .build();

        // 第一次调用：SchemaOnlyTool 没有本地实现，Agent 返回 TOOL_SUSPENDED。
        Msg suspended = agent.call("查询订单 " + orderId, context).block();
        if (suspended == null || suspended.getGenerateReason() != GenerateReason.TOOL_SUSPENDED) {
            throw new IllegalStateException("预期进入 TOOL_SUSPENDED，实际为：" + (suspended == null ? null : suspended.getGenerateReason()));
        }

        ToolUseBlock pendingTool = suspended.getContentBlocks(ToolUseBlock.class).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("没有找到待外部执行的工具调用"));

        System.out.println("挂起工具：" + pendingTool.getName());
        System.out.println("工具调用 ID：" + pendingTool.getId());
        System.out.println("工具参数：" + pendingTool.getInput());

        // 这里模拟框架外部执行；生产环境可以替换成远程服务、MQ、浏览器或人工输入。
        String externalResult = queryOrderSystem(String.valueOf(pendingTool.getInput().get("orderId")));

        Msg resultMessage = new ToolResultMessage(pendingTool.getId(), pendingTool.getName(), externalResult);

        // 第二次调用：按原 toolCallId 回填结果，Agent 从工具调用之后继续推理。
        Msg result = agent.call(List.of(resultMessage), context).block();
        if (result == null) {
            throw new IllegalStateException("Agent 恢复后没有返回结果");
        }

        System.out.println("恢复后停止原因：" + result.getGenerateReason());
        System.out.println("最终回答：" + result.getTextContent());
        return result;
    }

    private String queryOrderSystem(String orderId) {
        return "订单 " + orderId + " 状态：PAID";
    }
}
