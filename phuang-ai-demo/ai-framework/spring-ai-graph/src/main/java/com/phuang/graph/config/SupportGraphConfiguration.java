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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration(proxyBeanMethods = false)
public class SupportGraphConfiguration {

    // 这些 key 组成 Graph 的共享状态；节点通过返回 Map 更新状态，而不是直接修改状态对象。
    public static final String QUESTION = "question";
    public static final String NORMALIZED_QUESTION = "normalizedQuestion";
    public static final String ROUTE = "route";
    public static final String ANSWER = "answer";
    public static final String STATUS = "status";
    public static final String AUDIT = "audit";

    private static final String AUTO_ROUTE = "auto";
    private static final String MANUAL_ROUTE = "manual";
    private static final String NORMALIZE_NODE = "normalize";
    private static final String CLASSIFY_NODE = "classify";
    private static final String AUTO_REPLY_NODE = "auto_reply";
    private static final String MANUAL_REVIEW_NODE = "manual_review";

    private static final Set<String> MANUAL_REVIEW_KEYWORDS = Set.of(
            "退款", "扣费", "账单", "发票", "支付", "refund", "billing"
    );

    @Bean
    public CompiledGraph supportGraph() throws GraphStateException {
        // 先声明节点，再用边连接成有向图；node_async 将同步节点逻辑适配为 Graph 的异步执行接口。
        StateGraph graph = new StateGraph("support-routing", stateStrategies())
                .addNode(NORMALIZE_NODE, node_async(state -> {
                    String question = state.value(QUESTION, String.class).orElseThrow(() -> new IllegalArgumentException("question 不能为空"));
                    return Map.of(NORMALIZED_QUESTION, question.strip(),
                            AUDIT, List.of(NORMALIZE_NODE)
                    );
                }))
                .addNode(CLASSIFY_NODE, node_async(state -> {
                    String question = state.value(NORMALIZED_QUESTION, String.class).orElse("");
                    String lowerCaseQuestion = question.toLowerCase(Locale.ROOT);
                    String route = MANUAL_REVIEW_KEYWORDS.stream().anyMatch(lowerCaseQuestion::contains)
                            ? MANUAL_ROUTE : AUTO_ROUTE;
                    return Map.of(ROUTE, route,
                            AUDIT, List.of(CLASSIFY_NODE + ":" + route)
                    );
                }))
                .addNode(AUTO_REPLY_NODE, node_async(state -> {
                    String question = state.value(NORMALIZED_QUESTION, String.class).orElse("");
                    return Map.of(
                            ANSWER, "已自动处理：请根据帮助中心指引解决「" + question + "」。",
                            STATUS, "AUTO_REPLIED",
                            AUDIT, List.of(AUTO_REPLY_NODE)
                    );
                }))
                .addNode(MANUAL_REVIEW_NODE, node_async(state -> Map.of(
                        ANSWER, "该问题涉及资金操作，已转交人工客服审核。",
                        STATUS, "WAITING_FOR_HUMAN",
                        AUDIT, List.of(MANUAL_REVIEW_NODE)
                )))
                .addEdge(START, NORMALIZE_NODE)
                .addEdge(NORMALIZE_NODE, CLASSIFY_NODE)
                // 条件函数返回的 route 值必须与映射表的 key 对应，Graph 据此选择下一节点。
                .addConditionalEdges(
                        CLASSIFY_NODE,
                        edge_async(state -> state.value(ROUTE, String.class).orElse(AUTO_ROUTE)),
                        Map.of(AUTO_ROUTE, AUTO_REPLY_NODE, MANUAL_ROUTE, MANUAL_REVIEW_NODE)
                )
                .addEdge(AUTO_REPLY_NODE, END)
                .addEdge(MANUAL_REVIEW_NODE, END);

        // Checkpoint 按 RunnableConfig 中的 threadId 保存，便于后续读取同一会话的状态和执行历史。
        SaverConfig saverConfig = SaverConfig.builder()
                .register(new MemorySaver())
                .build();

        return graph.compile(CompileConfig.builder()
                .saverConfig(saverConfig)
                .build());
    }

    private KeyStrategyFactory stateStrategies() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            // 普通字段用新值覆盖；审计记录用 AppendStrategy 将每个节点写入的列表追加到已有列表。
            strategies.put(QUESTION, new ReplaceStrategy());
            strategies.put(NORMALIZED_QUESTION, new ReplaceStrategy());
            strategies.put(ROUTE, new ReplaceStrategy());
            strategies.put(ANSWER, new ReplaceStrategy());
            strategies.put(STATUS, new ReplaceStrategy());
            strategies.put(AUDIT, new AppendStrategy());
            return strategies;
        };
    }
}
