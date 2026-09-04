package com.phuang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(Embedding embedding, PgVector pgVector) {

    public record Embedding(
            String modelName,
            int dimensions,
            String baseUrl,
            String apiKey) {
    }

    public record PgVector(
            String host,
            int port,
            String user,
            String password,
            String database,
            String table) {
    }
}
