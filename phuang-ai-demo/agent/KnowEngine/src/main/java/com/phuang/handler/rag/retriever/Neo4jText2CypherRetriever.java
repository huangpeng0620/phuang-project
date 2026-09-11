package com.phuang.handler.rag.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 *
 * @description 自定义 Neo4j 图数据库内容检索器（Text2Cypher）
 * @author huangpeng
 * @since 2026/9/11
 */
@Slf4j
public class Neo4jText2CypherRetriever implements ContentRetriever {

    @Override
    public List<Content> retrieve(Query query) {
        return List.of();
    }
}
