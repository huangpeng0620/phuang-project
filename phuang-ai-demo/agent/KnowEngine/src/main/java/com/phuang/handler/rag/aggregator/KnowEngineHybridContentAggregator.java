package com.phuang.handler.rag.aggregator;

import com.phuang.util.ContentUtil;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;

import java.util.*;

/**
 * 混合内容聚合器，用于在同一批检索结果中分别处理结构化内容和非结构化内容
 * <p>
 * 处理规则：
 * <ol>
 *     <li>带有跳过重排标记的内容通常来自 SQL、Cypher 等精确查询，直接透传；</li>
 *     <li>其他内容通常来自向量检索或全文检索，交给下游聚合器执行 RRF 融合和模型重排序；</li>
 *     <li>最终将结构化结果放在前面，聚合后的非结构化结果放在后面。</li>
 * </ol>
 */
public class KnowEngineHybridContentAggregator implements ContentAggregator {

    /**
     * 负责融合和重排序非结构化检索结果的下游聚合器
     */
    private final ContentAggregator unstructuredAggregator;

    /**
     * 创建混合内容聚合器
     *
     * @param unstructuredAggregator 非结构化内容聚合器，例如 {@link KnowEngineReRankingContentAggregator}
     */
    @Builder
    public KnowEngineHybridContentAggregator(ContentAggregator unstructuredAggregator) {
        this.unstructuredAggregator = unstructuredAggregator;
    }

    /**
     * 按跳过重排标记拆分检索结果：结构化内容直接保留，非结构化内容保持原有的 Query 和
     * Retriever 列表层级后交给下游聚合器处理，最后按“结构化、非结构化”的顺序合并返回。
     *
     * @param queryToContents Query 与多路 Retriever 检索结果的映射
     * @return 合并后的内容列表，结构化结果在前，非结构化结果在后
     */
    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        // 没有检索结果时，无需执行内容分类和下游聚合
        if (queryToContents == null || queryToContents.isEmpty()) {
            return new ArrayList<>();
        }

        // 结构化内容直接透传；非结构化内容继续保留 Query 与多路结果的组织结构
        List<Content> structuredContents = new ArrayList<>();
        Map<Query, Collection<List<Content>>> unstructuredQueryToContents = new LinkedHashMap<>();

        // 逐个 Query 拆分其下各 Retriever 返回的内容
        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            Query query = entry.getKey();
            Collection<List<Content>> contentLists = entry.getValue();

            List<List<Content>> unstructuredLists = new ArrayList<>();
            for (List<Content> contents : contentLists) {
                List<Content> unstructured = new ArrayList<>();
                for (Content content : contents) {
                    // 带有 SKIP_RERANK 标记的精确查询结果绕过融合和模型重排序
                    if (ContentUtil.isSkipRerank(content)) {
                        structuredContents.add(content);
                    } else {
                        unstructured.add(content);
                    }
                }
                // 保留非空的 Retriever 结果列表，供下游按列表执行 RRF 融合
                if (!unstructured.isEmpty()) {
                    unstructuredLists.add(unstructured);
                }
            }

            // 当前 Query 没有非结构化内容时，不向下游传递空的结果集合
            if (!unstructuredLists.isEmpty()) {
                unstructuredQueryToContents.put(query, unstructuredLists);
            }
        }

        // 仅对非结构化内容执行融合、评分、过滤和重排序
        List<Content> unstructuredResults = unstructuredAggregator.aggregate(unstructuredQueryToContents);

        // 精确查询结果优先返回，重排序后的非结构化结果追加在其后
        List<Content> combined = new ArrayList<>(structuredContents.size() + unstructuredResults.size());
        combined.addAll(structuredContents);
        combined.addAll(unstructuredResults);
        return combined;
    }
}
