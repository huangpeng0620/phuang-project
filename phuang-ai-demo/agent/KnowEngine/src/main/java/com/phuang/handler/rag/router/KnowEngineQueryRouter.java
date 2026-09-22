package com.phuang.handler.rag.router;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.phuang.handler.rag.retriever.KnowEngineNeo4jContentRetriever;
import com.phuang.handler.rag.retriever.KnowEngineSqlDatabaseContentRetriever;
import com.phuang.handler.rag.retriever.ProgressAwareContentRetriever;
import com.phuang.model.dto.QueryRouteResult;
import com.phuang.util.JsonRepairUtil;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.elasticsearch.AbstractElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 *
 * @description Rag 查询路由器
 * <P>
 * 基于 JEV 结构化决策模型判断用户查询意图，将查询路由到最合适的内容检索器,支持三种数据源路由策略:
 *      1.关系型数据库 (relational_db): 适用于结构化数据查询，如车辆信息、保险信息、订单信息等,最终检索器: SQL
 *      2.图数据库 (graph_db): 适用于实体关系查询，如车型关系、影响链、层级结构等,最终检索器: Neo4j
 *      3.知识库 (knowledge_base): 适用于语义相似性查询，如售前咨询、售后支持、技术问题等,最终检索器: ES 向量 + 全文
 * 路由决策流程:
 *      1.通过 JEV HTTP 接口返回受限的结构化策略和置信度
 *      2.在本地将策略映射为对应的 Retriever 集合
 *      3.返回对应类型的 ContentRetriever 集
 *
 * JEV 未启用时保留原有 LLM 路由；JEV 调用失败、响应异常或置信度不足时，返回全部内容检索器作为降级处理，避免直接无结果。
 * </P>
 * @author huangpeng
 * @since 2026/9/8
 */
@Slf4j
public class KnowEngineQueryRouter implements QueryRouter {

    private final ChatModel chatModel;

    /**
     * JEV 路由客户端；启用后使用其替代 LLM 生成式路由
     */
    private final JevRouteClient jevRouteClient;

    protected final PromptTemplate promptTemplate;

    /**
     * 进度回调，用于流式返回前端进度信息
     */
    private final Consumer<String> progressCallback;

    /**
     * 内容检索器列表
     */
    private final Collection<ContentRetriever> contentRetrievers;

    /**
     * 确保路由进度只发送一次
     * <P>
     *     由于问题改写流程中可能将原始问题改成多个,这样一次RAG流程中可能同一个检索器需要执行多次,因此可以避免同一个检索器发送多次进度通知
     * </P>
     */
    private final AtomicBoolean routeProgressSent = new AtomicBoolean(false);

    public KnowEngineQueryRouter(Collection<ContentRetriever> contentRetrievers,
                                 ChatModel chatModel,
                                 JevRouteClient jevRouteClient,
                                 Consumer<String> progressCallback) {
        this(contentRetrievers, QUERY_ROUTE_PROMPT, chatModel, jevRouteClient, progressCallback);
    }

    public KnowEngineQueryRouter(Collection<ContentRetriever> contentRetrievers,
                                 PromptTemplate promptTemplate,
                                 ChatModel chatModel,
                                 JevRouteClient jevRouteClient,
                                 Consumer<String> progressCallback) {
        this.promptTemplate = getOrDefault(promptTemplate, QUERY_ROUTE_PROMPT);
        this.contentRetrievers = contentRetrievers;
        this.chatModel = chatModel;
        this.jevRouteClient = jevRouteClient;
        this.progressCallback = progressCallback;
    }

    private static final PromptTemplate QUERY_ROUTE_PROMPT = PromptTemplate.from("""
            你是一个汽车领域的智能助手，负责理解用户的问题，并智能判断最适合的数据查询方式。你的任务不是直接回答问题，而是分析问题语义，决定应调用哪种或哪几种数据源来获取答案。
            
            请根据以下规则进行判断：
            1、关系型数据库（Relational DB）适用场景：
            问题涉及结构化数据查询（如“车辆信息”、“保险信息”、“订单信息”等）
            问题涉及到用户个人拥有的车辆相关信息的查询的，如查询发动机号、查询下次保养时间、查询车辆里程等
            包含明确的实体属性、时间范围、数值比较、聚合操作（如 SUM、COUNT、AVG）
            示例：“我的保险还有多少天到期？”
            
            2、图数据库（Graph DB）适用场景：
            问题关注实体之间的关系、路径、连接性、层级或网络结构
            出现关键词如“谁的发动机是...”、“A和B之间有什么联系？”、“最短路径”、“影响链”
            示例：“纯电车型都有哪些？”、“型号A和型号B有什么关系？”
            
            3、知识库检索适用场景：
            问题基于语义相似性、模糊匹配、非结构化文本理解
            涉及“类似”、“相关”、“推荐”、“总结”、“解释某段内容”等意图
            涉及到汽车相关售前、售后、技术支持、营销政策等问题
            示例：“发动机异响怎么处理？”、“如何打开零重力座椅？”
            
            请严格按以下 JSON 格式输出决策结果，不要添加额外解释，不要添加任何markdown符号，如[```]：
            
            {
              "intent": "简要概括用户问题的核心意图",
              "strategy": "relational_db"
              "reasoning": "简明说明判断依据",
              "confidence": 置信度，0-1之间的小数
            }
            
            注意：
            strategy 仅使用以下三个字符串值："relational_db"、"graph_db"、"knowledge_base"，其一次只返回一个。
            confidence 表示你对策略推荐的置信度（0–1），评分保留两位小数
            reasoning 应简洁说明判断依据
            
            用户的原始查询：{{query}}
            """);

    @Override
    public Collection<ContentRetriever> route(Query query) {
        // 发送进度：开始问题路由（仅发送一次，避免多个 query 导致重复）
        if (Objects.nonNull(progressCallback) && routeProgressSent.compareAndSet(false, true)) {
            progressCallback.accept("[PROGRESS]:正在路由您的问题...");
            log.info("[PROGRESS]:正在路由您的问题...");
        }
        try {
            String strategy;
            if (jevRouteClient != null && jevRouteClient.isEnabled()) {
                JevRouteClient.RouteDecision decision = jevRouteClient.route(query.text());
                strategy = decision.strategy();
                log.info("JEV route success, strategy: {}, confidence: {}", strategy, decision.confidence());
            } else {
                // 未启用 JEV 时沿用原有 LLM 路由，确保现有部署无需配置 JEV 也保持原行为
                String response = chatModel.chat(createPrompt(query).text());
                QueryRouteResult queryRouteResult = JSON.parseObject(JsonRepairUtil.fixJson(response), QueryRouteResult.class);
                strategy = queryRouteResult.getStrategy();
                log.info("LLM route success, strategy: {}", strategy);
            }

            return selectRetrievers(strategy);
        } catch (JSONException jsonException) {
            log.error("LLM 路由响应解析失败", jsonException);
        } catch (Exception e) {
            log.error("路由决策失败", e);
        }
        // JEV 路由失败、置信度不足或 LLM 路由异常时降级为全量检索，避免直接无结果
        return contentRetrievers;
    }

    /**
     * 将模型返回的策略映射为项目内的 Retriever 集合。
     */
    private Collection<ContentRetriever> selectRetrievers(String strategy) {
        switch (strategy) {
            case "relational_db":
                return contentRetrievers.stream().filter(retriever -> {
                    if (retriever instanceof ProgressAwareContentRetriever) {
                        ContentRetriever delegate = ((ProgressAwareContentRetriever) retriever).getDelegate();
                        return delegate instanceof SqlDatabaseContentRetriever || delegate instanceof KnowEngineSqlDatabaseContentRetriever;
                    }
                    return retriever instanceof SqlDatabaseContentRetriever || retriever instanceof KnowEngineSqlDatabaseContentRetriever;

                }).collect(Collectors.toList());
            case "graph_db":
                return contentRetrievers.stream().filter(retriever -> {
                    if (retriever instanceof ProgressAwareContentRetriever) {
                        ContentRetriever delegate = ((ProgressAwareContentRetriever) retriever).getDelegate();
                        return delegate instanceof Neo4jText2CypherRetriever || delegate instanceof KnowEngineNeo4jContentRetriever;
                    }
                    return retriever instanceof Neo4jText2CypherRetriever || retriever instanceof KnowEngineNeo4jContentRetriever;

                }).collect(Collectors.toList());
            case "knowledge_base":
                return contentRetrievers.stream().filter(retriever -> {
                    if (retriever instanceof ProgressAwareContentRetriever) {
                        return ((ProgressAwareContentRetriever) retriever).getDelegate() instanceof AbstractElasticsearchEmbeddingStore;
                    }
                    return retriever instanceof AbstractElasticsearchEmbeddingStore;
                }).collect(Collectors.toList());
            default:
                return contentRetrievers;
        }
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        return promptTemplate.apply(variables);
    }
}
