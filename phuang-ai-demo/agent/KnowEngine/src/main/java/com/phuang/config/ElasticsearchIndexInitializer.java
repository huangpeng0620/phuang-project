package com.phuang.config;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 在向量存储初始化前显式创建 Elasticsearch 索引
 *
 * <p>已存在的索引只复用、不修改，避免应用启动时覆盖已有 mapping 或数据 </p>
 */
@Component
@Slf4j
public class ElasticsearchIndexInitializer {

    private static final int HTTP_NOT_FOUND = 404;

    private final RestClient restClient;

    public ElasticsearchIndexInitializer(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 索引不存在时，使用明确的向量维度和字段 mapping 创建索引
     *
     * @param indexName 索引名称
     * @param dimensions 向量维度
     */
    public void createIfAbsent(String indexName, int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Elasticsearch 向量维度必须大于 0");
        }
        try {
            if (indexExists(indexName)) {
                log.info("Elasticsearch 索引已存在，直接复用: {}", indexName);
                return;
            }
            Request createIndexRequest = new Request("PUT", "/" + indexName);
            createIndexRequest.setJsonEntity(createIndexMapping(dimensions));
            restClient.performRequest(createIndexRequest);
            log.info("Elasticsearch 索引创建成功: {}, dimensions: {}", indexName, dimensions);
        } catch (ResponseException e) {
            // 多实例可能同时通过存在性检查；若另一实例已成功创建，则直接复用。
            if (indexExistsAfterConcurrentCreate(indexName)) {
                log.info("Elasticsearch 索引已由其他实例创建，直接复用: {}", indexName);
                return;
            }
            throw new IllegalStateException("创建 Elasticsearch 索引失败: " + indexName, e);
        } catch (IOException e) {
            throw new IllegalStateException("访问 Elasticsearch 索引失败: " + indexName, e);
        }
    }

    /**
     * 校验索引是否存在
     * @param indexName
     * @return
     * @throws IOException
     */
    private boolean indexExists(String indexName) throws IOException {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/" + indexName));
            return response.getStatusLine().getStatusCode() != HTTP_NOT_FOUND;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == HTTP_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private boolean indexExistsAfterConcurrentCreate(String indexName) {
        try {
            return indexExists(indexName);
        } catch (IOException ignored) {
            return false;
        }
    }

    private String createIndexMapping(int dimensions) {
        return """
                {
                  "mappings": {
                    "properties": {
                      "metadata": {
                        "dynamic": true,
                        "properties": {
                          "accessibleBy": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "chunkId": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "docId": {
                            "type": "long"
                          },
                          "fileName": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "headerLevel": {
                            "type": "long"
                          },
                          "parentChunkId": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "subtitle": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "url": {
                            "type": "text",
                            "fields": {
                              "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                              }
                            }
                          },
                          "version": {
                            "type": "long"
                          }
                        }
                      },
                      "text": {
                        "type": "text",
                        "fields": {
                          "keyword": {
                            "type": "keyword",
                            "ignore_above": 256
                          }
                        }
                      },
                      "vector": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine",
                        "index_options": {
                          "type": "int8_hnsw",
                          "m": 16,
                          "ef_construction": 100
                        }
                      }
                    }
                  }
                }
                """.formatted(dimensions);
    }
}
