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
     *     第一次向该索引写入向量数据时,Elasticsearch 会自动创建该索引
     * </P>
     */
    public static final String INDEX_NAME = "know-engine-vector";

    /**
     * 定义 ElasticsearchEmbeddingStore
     * @param restClient
     * @return
     */
    @Primary
    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchEmbeddingStore elasticsearchEmbeddingStore(RestClient restClient) {
        return ElasticsearchEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(INDEX_NAME)
                .build();
    }

    /**
     * 定义 Embedding 向量模型
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
     * 配置 ES 客户端
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
