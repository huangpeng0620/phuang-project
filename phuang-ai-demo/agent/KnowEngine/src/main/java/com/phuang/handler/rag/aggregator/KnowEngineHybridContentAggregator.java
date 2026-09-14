package com.phuang.handler.rag.aggregator;

import com.phuang.util.ContentUtil;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;

import java.util.*;

/**
 * 混合内容聚合器
 * <p>
 *    将检索结果分为两类:
 *     1. 结构化结果：来自 SQL/Cypher 查询,这些通常是数据库精确查询结果,无需参与 RRF 融合和 scoring model 重排序
 *     2. 非结构化结果: 来自向量/全文检索，需要委托给底层聚合器（如重排序聚合器）进行融合和重排序
 * 最终输出顺序：结构化结果在前，非结构化结果在后
 */
public class KnowEngineHybridContentAggregator implements ContentAggregator {

    private final ContentAggregator unstructuredAggregator;

    @Builder
    public KnowEngineHybridContentAggregator(ContentAggregator unstructuredAggregator) {
        this.unstructuredAggregator = unstructuredAggregator;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        if (queryToContents == null || queryToContents.isEmpty()) {
            return new ArrayList<>();
        }

        List<Content> structuredContents = new ArrayList<>();
        Map<Query, Collection<List<Content>>> unstructuredQueryToContents = new LinkedHashMap<>();

        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            Query query = entry.getKey();
            Collection<List<Content>> contentLists = entry.getValue();

            List<List<Content>> unstructuredLists = new ArrayList<>();
            for (List<Content> contents : contentLists) {
                List<Content> unstructured = new ArrayList<>();
                for (Content content : contents) {
                    if (ContentUtil.isSkipRerank(content)) {
                        structuredContents.add(content);
                    } else {
                        unstructured.add(content);
                    }
                }
                if (!unstructured.isEmpty()) {
                    unstructuredLists.add(unstructured);
                }
            }

            if (!unstructuredLists.isEmpty()) {
                unstructuredQueryToContents.put(query, unstructuredLists);
            }
        }

        List<Content> unstructuredResults = unstructuredAggregator.aggregate(unstructuredQueryToContents);

        List<Content> combined = new ArrayList<>(structuredContents.size() + unstructuredResults.size());
        combined.addAll(structuredContents);
        combined.addAll(unstructuredResults);
        return combined;
    }
}
