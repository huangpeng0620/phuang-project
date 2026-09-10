package com.phuang.config;

import com.phuang.model.ElasticSearchProperties;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import jakarta.annotation.Resource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfiguration {

    @Resource
    private ElasticSearchProperties properties;

    /**
     * ES 索引名称
     * <P>
     *     创建 ElasticsearchEmbeddingStore 前显式检查并创建该索引
     * </P>
     */
    public static final String INDEX_NAME = "know-engine-vector";

    /**
     * 定义 ElasticsearchEmbeddingStore 向量存储
     * <P>
     *     1. @ConditionalOnMissingBean: 项目没有自定义同类型 Bean 时才创建
     *     2. @Primary: 存在多个候选 Bean 时优先注入这个
     * </P>
     * @param restClient
     * @return
     */
    @Primary
    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchEmbeddingStore elasticsearchEmbeddingStore(RestClient restClient,
                                                                   ElasticsearchIndexInitializer indexInitializer) {
        // 索引手动初始化
        indexInitializer.createIfAbsent(INDEX_NAME, properties.getDimensions());
        return ElasticsearchEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(INDEX_NAME)
                .build();
    }

    /**
     * 定义 langchain4j Embedding 向量模型
     * <P>
     *  1. maxSegmentsPerBatch = 9 表示批量向量化时每批最多发送 9 个文本分片
     * </P>
     * @return
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .modelName(properties.getModelName())
                .dimensions(properties.getDimensions())
                .baseUrl(properties.getBaseUrl())
                .maxSegmentsPerBatch(9)
                .apiKey(properties.getApiKey())
                .build();
    }

    /**
     * 创建底层 Elasticsearch HTTP 客户端
     * <P>
     *     1. destroyMethod = "close" 使得 Spring 容器关闭时自动调用 close()
     *     2. @ConditionalOnMissingBean 使得项目没有自定义同类型 Bean 时才创建
     * </P>
     * @return
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient restClient() {
        return RestClient
                .builder(HttpHost.create(properties.getHost()))
                .build();
    }
}
