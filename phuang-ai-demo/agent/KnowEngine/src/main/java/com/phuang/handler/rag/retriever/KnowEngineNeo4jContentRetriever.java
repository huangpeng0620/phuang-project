package com.phuang.handler.rag.retriever;

import cn.hutool.core.collection.CollectionUtil;
import com.phuang.util.ContentUtil;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @description 自定义 Neo4j 图数据库内容检索器（Text2Cypher）
 *
 * @see Neo4jText2CypherRetriever
 */
@Slf4j
public class KnowEngineNeo4jContentRetriever implements ContentRetriever {

    /**
     * -- GETTER --
     *  获取内部的 Neo4jText2CypherRetriever 实例
     */
    @Getter
    private final Neo4jText2CypherRetriever neo4jText2CypherRetriever;

    /**
     * 获取兜底的知识库检索器
     * -- GETTER --
     *  获取兜底的知识库检索器

     */
    @Getter
    private final ContentRetriever fallbackRetriever;

    private final String userId;

    public KnowEngineNeo4jContentRetriever(Neo4jText2CypherRetriever neo4jText2CypherRetriever,
                                           ContentRetriever fallbackRetriever,
                                           String userId) {
        this.neo4jText2CypherRetriever = neo4jText2CypherRetriever;
        this.fallbackRetriever = fallbackRetriever;
        this.userId = userId;
    }

    private static final String QUESTION_TEMPLATE = """
            用户问题是: %s,
            用户信息是: %s,
            当前时间: %s
            """;

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> results = Lists.newArrayList();
        try {
            // 与 SQL 检索器保持一致：在问题中补充用户上下文与时间信息，辅助 LLM 生成更准确的 Cypher
            query = new Query(String.format(QUESTION_TEMPLATE, query.text(), userId, LocalDateTime.now()), query.metadata());
            results = neo4jText2CypherRetriever.retrieve(query);
        } catch (Exception e) {
            log.warn("Neo4j 图数据库检索异常，降级使用知识库检索, query: {}", query.text(), e);
            return fallbackRetriever.retrieve(query);
        }

        if (CollectionUtil.isEmpty(results) || isCypherResultEmpty(results)) {
            log.info("Neo4j 图数据库检索结果为空，降级使用知识库检索, query: {}", query.text());
            return fallbackRetriever.retrieve(query);
        }

        // Cypher 结构化查询结果直接透传，不参与后续重排序/融合
        return results.stream()
                .map(ContentUtil::markAsSkipRerank)
                .collect(Collectors.toList());
    }

    /**
     * 判断 Cypher 查询结果是否实际为空
     * <p>
     * Neo4jText2CypherRetriever 返回的 Content 文本通常形如：
     *      <pre>
     *          Result of executing '...CYPHER...':
     *      key1,key2
     *      </pre>
     * 当只有列名头部而没有实际数据行时，认为结果为空
     * <p>
     * 通过定位最后一个 "':\n" 标记（Cypher 语句描述结束位置）,判断列名行之后是否存在实际数据行，避免 Cypher 语句本身含换行符导致误判
     */
    private boolean isCypherResultEmpty(List<Content> results) {
        if (results.size() != 1) {
            return false;
        }
        String text = results.get(0).textSegment().text();
        if (!text.startsWith("Result of executing '")) {
            return false;
        }
        // ":\n" 标记列名开始，列名后的第一个 "\n" 标记数据开始
        int columnStartIndex = text.indexOf(":\n");
        if (columnStartIndex == -1) {
            return false;
        }
        // ":\n" 之后是列名行，找列名行结束的 "\n"（即数据开始位置）
        int dataStartIndex = text.indexOf('\n', columnStartIndex + 2);
        // 列名后没有换行符，或换行符后无实际内容，则表示无数据
        return dataStartIndex == -1 || text.substring(dataStartIndex + 1).trim().isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Neo4jGraph graph;
        private PromptTemplate promptTemplate;
        private List<String> examples;
        private List<String> relationships;
        private String dialect;
        private int maxRetries = 1;
        private ChatModel chatModel;
        private ContentRetriever fallbackRetriever;
        private String userId;

        public Builder graph(Neo4jGraph graph) {
            this.graph = graph;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples;
            return this;
        }

        public Builder relationships(List<String> relationships) {
            this.relationships = relationships;
            return this;
        }

        public Builder dialect(String dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder fallbackRetriever(ContentRetriever fallbackRetriever) {
            this.fallbackRetriever = fallbackRetriever;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public KnowEngineNeo4jContentRetriever build() {
            Neo4jText2CypherRetriever.Builder neo4jBuilder = Neo4jText2CypherRetriever.builder()
                    .graph(graph)
                    .chatModel(chatModel)
                    .maxRetries(maxRetries);

            if (promptTemplate != null) {
                neo4jBuilder.promptTemplate(promptTemplate);
            }
            if (examples != null) {
                neo4jBuilder.examples(examples);
            }
            if (relationships != null) {
                neo4jBuilder.relationships(relationships);
            }
            if (dialect != null) {
                neo4jBuilder.dialect(dialect);
            }

            return new KnowEngineNeo4jContentRetriever(neo4jBuilder.build(), fallbackRetriever, userId);
        }
    }


}
