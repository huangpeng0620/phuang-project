package com.phuang.graph.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.phuang.graph.workflow.EmailWorkflowNodes;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.phuang.graph.workflow.EmailWorkflowState.*;

/** 负责声明邮件处理工作流的节点、边、状态合并策略和 checkpoint 行为。 */
@Configuration(proxyBeanMethods = false)
public class SupportGraphConfiguration {

    @Bean
    public CompiledGraph supportGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        // 各节点共享同一个 ChatClient；目前只有“分类”和“草拟回复”会调用大模型。
        EmailWorkflowNodes nodes = new EmailWorkflowNodes(chatClientBuilder.build());

        StateGraph graph = new StateGraph("customer-email-support", stateStrategies())
                .addNode(READ_EMAIL, node_async(nodes.readEmail()))
                .addNode(CLASSIFY_INTENT, node_async(nodes.classifyIntent()))
                .addNode(SEARCH_DOCUMENTATION, node_async(nodes.searchDocumentation()))
                .addNode(BUG_TRACKING, node_async(nodes.bugTracking()))
                .addNode(DRAFT_RESPONSE_NODE, node_async(nodes.draftResponse()))
                .addNode(HUMAN_REVIEW, node_async(nodes.humanReview()))
                .addNode(SEND_REPLY, node_async(nodes.sendReply()))
                .addEdge(START, READ_EMAIL)
                .addEdge(READ_EMAIL, CLASSIFY_INTENT)
                // 分类节点写入 next_node；条件边据此选择查文档、建工单、起草或直接人工审核。
                .addConditionalEdges(
                        CLASSIFY_INTENT,
                        edge_async(state -> state.value(NEXT_NODE, String.class).orElse(DRAFT_RESPONSE_NODE)),
                        Map.of(
                                SEARCH_DOCUMENTATION, SEARCH_DOCUMENTATION,
                                BUG_TRACKING, BUG_TRACKING,
                                DRAFT_RESPONSE_NODE, DRAFT_RESPONSE_NODE,
                                HUMAN_REVIEW, HUMAN_REVIEW
                        )
                )
                // 检索/工单结果都作为上下文交给草稿节点。
                .addEdge(SEARCH_DOCUMENTATION, DRAFT_RESPONSE_NODE)
                .addEdge(BUG_TRACKING, DRAFT_RESPONSE_NODE)
                // 高风险或复杂问题在草拟后转人工；其余邮件自动进入发送节点。
                .addConditionalEdges(
                        DRAFT_RESPONSE_NODE,
                        edge_async(state -> state.value(NEXT_NODE, String.class).orElse(SEND_REPLY)),
                        Map.of(HUMAN_REVIEW, HUMAN_REVIEW, SEND_REPLY, SEND_REPLY)
                )
                // 审核通过后发送；审核拒绝时 next_node 为 END，流程不再向外部发送。
                .addConditionalEdges(
                        HUMAN_REVIEW,
                        edge_async(state -> state.value(NEXT_NODE, String.class).orElse(END)),
                        Map.of(SEND_REPLY, SEND_REPLY, END, END)
                )
                .addEdge(SEND_REPLY, END);

        // MemorySaver 会按 RunnableConfig.threadId 隔离并保存每封邮件的中间状态。
        SaverConfig saverConfig = SaverConfig.builder()
                .register(new MemorySaver())
                .build();

        return graph.compile(CompileConfig.builder()
                .saverConfig(saverConfig)
                // 进入 human_review 前暂停：外部审核人可先查看 checkpoint，再写入决定并恢复。
                .interruptBefore(HUMAN_REVIEW)
                .build());
    }

    /**
     * 每个共享字段都明确指定合并规则。
     * 普通业务字段使用 ReplaceStrategy 覆盖为节点新值；messages 用 AppendStrategy 累加记录。
     */
    private KeyStrategyFactory stateStrategies() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            for (String key : new String[]{
                    EMAIL_ID, SENDER_EMAIL, EMAIL_CONTENT, CLASSIFICATION, CUSTOMER_HISTORY, SEARCH_RESULTS, TICKET_ID,
                    DRAFT_RESPONSE, EDITED_RESPONSE, SENT_RESPONSE, REVIEW_DATA, APPROVED,
                    NEXT_NODE, CURRENT_STEP, STATUS
            }) {
                strategies.put(key, new ReplaceStrategy());
            }
            strategies.put(MESSAGES, new AppendStrategy());
            return strategies;
        };
    }
}
