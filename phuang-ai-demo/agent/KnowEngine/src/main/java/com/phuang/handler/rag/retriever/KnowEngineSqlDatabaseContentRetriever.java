package com.phuang.handler.rag.retriever;

import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;

/**
 *
 * @description 自定义 SQL 数据库内容检索器
 * @author huangpeng
 * @since 2026/9/11
 */
@Slf4j
public class KnowEngineSqlDatabaseContentRetriever implements ContentRetriever {

    private final SqlDatabaseContentRetriever sqlDatabaseContentRetriever;

    private final ContentRetriever fallbackRetriever;

    public KnowEngineSqlDatabaseContentRetriever(SqlDatabaseContentRetriever sqlDatabaseContentRetriever,
                                                 ContentRetriever fallbackRetriever) {
        this.sqlDatabaseContentRetriever = sqlDatabaseContentRetriever;
        this.fallbackRetriever = fallbackRetriever;
    }

    @Override
    public List<Content> retrieve(Query query) {
        //todo
        return List.of();
    }

    /**
     * 获取内部的 SqlDatabaseContentRetriever 实例
     */
    public SqlDatabaseContentRetriever getSqlDatabaseContentRetriever() {
        return sqlDatabaseContentRetriever;
    }

    /**
     * 获取兜底的知识库检索器
     */
    public ContentRetriever getFallbackRetriever() {
        return fallbackRetriever;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DataSource dataSource;
        private PromptTemplate promptTemplate;
        private String databaseStructure;
        private ChatModel chatModel;
        private ContentRetriever fallbackRetriever;

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder databaseStructure(String databaseStructure) {
            this.databaseStructure = databaseStructure;
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

        public KnowEngineSqlDatabaseContentRetriever build() {
            SqlDatabaseContentRetriever sqlRetriever = SqlDatabaseContentRetriever.builder()
                    .dataSource(dataSource)
                    .promptTemplate(promptTemplate)
                    .databaseStructure(databaseStructure)
                    .chatModel(chatModel)
                    .build();
            return new KnowEngineSqlDatabaseContentRetriever(sqlRetriever, fallbackRetriever);
        }
    }
}
