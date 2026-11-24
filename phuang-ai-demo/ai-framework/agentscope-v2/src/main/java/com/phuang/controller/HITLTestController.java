package com.phuang.controller;

import com.phuang.tool.ArticleTools;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.*;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 *
 * @description HITLTestController
 * @author huangpeng
 * @since 2026/11/18
 */
@RestController
@RequestMapping("hook/confirmationHook")
public class HITLTestController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("/chat")
    public Msg chat() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ArticleTools());

        PermissionContextState permissions = PermissionContextState.builder()
                .addAskRule(
                        "publish_article",
                        new PermissionRule("publish_article", null, PermissionBehavior.ASK, "发布文章前必须人工确认"))
                .build();

        HarnessAgent agent = HarnessAgent.builder()
                .name("article-agent")
                .sysPrompt("用户要求发布文章时，调用 publish_article 工具。")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .permissionContext(permissions)
                .build();

        RuntimeContext context = RuntimeContext.builder()
                .userId("user-001")
                .sessionId("session-001")
                .build();

        /**
         *  第一次调用：模型生成工具调用，权限引擎在真正执行工具前暂停
         */
        Msg paused = agent.call("请发布文章《AgentScope 入门》", context).block();
        if (paused == null || paused.getGenerateReason() != GenerateReason.PERMISSION_ASKING) {
            throw new IllegalStateException("预期进入 PERMISSION_ASKING，实际为：" + (paused == null ? null : paused.getGenerateReason()));
        }

        ToolUseBlock pendingTool = paused.getContentBlocks(ToolUseBlock.class).stream()
                .filter(tool -> tool.getState() == ToolCallState.ASKING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有找到待确认的工具调用"));

        System.out.println("等待确认的工具:" + pendingTool.getName());
        System.out.println("工具调用 ID:" + pendingTool.getId());
        System.out.println("工具参数:" + pendingTool.getInput());

        Msg confirmMessage = UserMessage.builder()
                .textContent("")
                .metadata(Map.of(
                        Msg.METADATA_CONFIRM_RESULTS, List.of(new ConfirmResult(Boolean.TRUE, pendingTool))))
                .build();

        /**
         * 第二次调用：框架找到 ASKING 工具；批准则执行，拒绝则写入 DENIED 结果
         */
        Msg result = agent.call(List.of(confirmMessage), context).block();
        if (result == null) {
            throw new IllegalStateException("Agent 恢复后没有返回结果");
        }

        System.out.println("恢复后停止原因：" + result.getGenerateReason());
        System.out.println("最终回答：" + result.getTextContent());
        return result;

    }

}
