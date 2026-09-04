package com.phuang.config;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(RagProperties properties) {
        RagProperties.Embedding embedding = properties.embedding();
        return OpenAiEmbeddingModel.builder()
                .modelName(embedding.modelName())
                .dimensions(embedding.dimensions())
                .baseUrl(embedding.baseUrl())
                .apiKey(embedding.apiKey())
                .build();
    }

    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore(OpenAiEmbeddingModel embeddingModel,
                                                         RagProperties properties) {
        RagProperties.PgVector pgVector = properties.pgVector();
        return PgVectorEmbeddingStore.builder()
                .host(pgVector.host())
                .port(pgVector.port())
                .user(pgVector.user())
                .password(pgVector.password())
                .database(pgVector.database())
                .table(pgVector.table())
                .dimension(embeddingModel.dimension())
                .build();
    }
}
