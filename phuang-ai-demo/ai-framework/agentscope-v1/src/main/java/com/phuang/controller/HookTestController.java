package com.phuang.controller;

import com.phuang.hook.ConfirmationHook;
import com.phuang.tool.SensitiveTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("hook/confirmationHook")
public class HookTestController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @Resource
    private MysqlSession mysqlSession;

    /**
     * 智能体对话(HIL)
     * @param message 消息
     * @param sessionId 会话ID
     * @param approvalType 审批类型: 1-同意,0-拒绝
     * @return
     */
    @GetMapping("/chat")
    public HitlResponse chat(@RequestParam(value = "message", required = false) String message,
                             @RequestParam(value = "sessionId", required = false) String sessionId,
                             @RequestParam(value = "approvalType", required = false) Integer approvalType) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        InMemoryMemory memory = new InMemoryMemory();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SensitiveTool());
        ReActAgent reActAgent = ReActAgent.builder()
                .name("assistant")
                .sysPrompt("用户要求删除文件时调用 delete_file 工具；工具执行前会等待用户确认。")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .hooks(List.of(new ConfirmationHook()))
                .memory(memory)
                .build();
        //加载会话
        reActAgent.loadIfExists(mysqlSession, sessionId);

        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();

        Msg msg = null;
        if (approvalType == null) {
            msg = reActAgent.call(userMsg).block();
        } else {
            //HIL 审批
            if (approvalType == 1) {
                // 用户确认，继续执行
                msg = reActAgent.call().block();
            } else if (approvalType == 0) {
                // 用户拒绝，返回取消信息
                // 从 memory 里找到最近一条包含 ToolUseBlock 的 assistant 消息
                Msg pendingMsg = memory.getMessages().stream()
                        .filter(m -> m.getRole() == MsgRole.ASSISTANT)
                        .filter(m -> m.hasContentBlocks(ToolUseBlock.class))
                        .reduce((first, next) -> next)
                        .orElseThrow();
                List<ToolUseBlock> pending = pendingMsg.getContentBlocks(ToolUseBlock.class);
                Msg cancelResult = Msg.builder()
                        .role(MsgRole.TOOL)
                        .content(pending.stream()
                                .map(t -> ToolResultBlock.of(t.getId(), t.getName(),
                                        TextBlock.builder().text("操作已取消").build()))
                                .toArray(ToolResultBlock[]::new))
                        .build();
                msg = reActAgent.call(cancelResult).block();
            }
        }

        HitlResponse hitlResponse = new HitlResponse(sessionId, "COMPLETED", msg.getTextContent(), null);
        // 检查是否有待确认的工具调用
        if (msg.hasContentBlocks(ToolUseBlock.class)) {
            // 展示待执行的工具
            List<ToolUseBlock> pending = msg.getContentBlocks(ToolUseBlock.class);
            if (!pending.isEmpty()) {
                List<ToolCallView> calls = pending.stream()
                        .map(tool -> new ToolCallView(tool.getName(), tool.getInput()))
                        .toList();
                hitlResponse = new HitlResponse(sessionId, "WAITING_FOR_APPROVAL", null, calls);
            }
        }

        //保存会话
        reActAgent.saveTo(mysqlSession, sessionId);
        return hitlResponse;
    }

    public record ToolCallView(String name, Map<String, Object> arguments) {
    }

    public record HitlResponse(
            String sessionId,
            String status,
            String reply,
            List<ToolCallView> pendingTools) {
    }
}
