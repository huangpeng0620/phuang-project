package com.phuang.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 *
 * @description 敏感操作确认 hook
 * @author huangpeng
 * @since 2026/10/31
 */
public class ConfirmationHook implements Hook {

    private static final List<String> SENSITIVE_TOOLS = List.of("delete_file", "send_email");

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent e) {
            Msg reasoningMsg = e.getReasoningMessage();
            if (reasoningMsg == null) {
                return Mono.just(event);
            }
            List<ToolUseBlock> toolCalls = reasoningMsg.getContentBlocks(ToolUseBlock.class);

            // 如果包含敏感工具，暂停等待确认
            boolean hasSensitive = toolCalls.stream()
                    .anyMatch(t -> SENSITIVE_TOOLS.contains(t.getName()));

            if (hasSensitive) {
                e.stopAgent();
            }
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 1;
    }
}
