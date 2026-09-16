package com.phuang.handler.rag.retriever;

import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 *
 * @description Retriever 装饰器
 * <P>
 *     在真正执行检索之前, 根据被包装的 Retriever 类型向前端【流式发送进度消息】, 然后将查询原样委托给真正的 Retriever
 * </P>
 * @author huangpeng
 * @since 2026/9/11
 */
@Slf4j
@Data
public class ProgressAwareContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;

    private final Consumer<String> progressCallback;

    /**
     * 确保路由进度只发送一次
     * <P>
     *     由于问题改写流程中可能将原始问题改成多个,这样一次RAG流程中可能同一个检索器需要执行多次,因此可以避免同一个检索器发送多次进度通知
     * </P>
     */
    private final AtomicBoolean embeddingProgressSent = new AtomicBoolean(false);
    private final AtomicBoolean sqlProgressSent = new AtomicBoolean(false);
    private final AtomicBoolean neo4jProgressSent = new AtomicBoolean(false);

    /**
     * 创建带进度通知能力的 Retriever 装饰器。
     *
     * @param delegate         实际执行检索的 Retriever，不能为空
     * @param progressCallback 检索进度回调；为空时仅执行检索，不发送进度消息
     */
    @Builder
    public ProgressAwareContentRetriever(ContentRetriever delegate, Consumer<String> progressCallback) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.progressCallback = progressCallback;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (Objects.nonNull(progressCallback)) {
            switch (delegate) {
                case KnowEngineSqlDatabaseContentRetriever knowEngineSqlDatabaseContentRetriever -> {
                    if (sqlProgressSent.compareAndSet(false, true)) {
                        progressCallback.accept("[PROGRESS]:正在检索数据库内容...");
                        log.info("[PROGRESS]:正在检索【knowEngineSqlDatabaseContentRetriever】数据库内容...");
                    }
                }
                case SqlDatabaseContentRetriever sqlDatabaseContentRetriever -> {
                    if (sqlProgressSent.compareAndSet(false, true)) {
                        progressCallback.accept("[PROGRESS]:正在检索数据库内容...");
                        log.info("[PROGRESS]:正在检索【sqlDatabaseContentRetriever】数据库内容...");
                    }
                }
                case Neo4jText2CypherRetriever neo4jText2CypherRetriever -> {
                    if (neo4jProgressSent.compareAndSet(false, true)) {
                        progressCallback.accept("[PROGRESS]:正在检索图数据库内容...");
                        log.info("[PROGRESS]:正在检索【neo4jText2CypherRetriever】数据库内容...");
                    }
                }
                case KnowEngineNeo4jContentRetriever knowEngineNeo4jContentRetriever -> {
                    if (neo4jProgressSent.compareAndSet(false, true)) {
                        progressCallback.accept("[PROGRESS]:正在检索图数据库内容...");
                        log.info("[PROGRESS]:正在检索【knowEngineNeo4jContentRetriever】数据库内容...");
                    }
                }
                case KnowEngineElasticsearchContentRetriever knowEngineElasticsearchContentRetriever -> {
                    ElasticsearchConfiguration configuration = knowEngineElasticsearchContentRetriever.getConfiguration();
                    if (embeddingProgressSent.compareAndSet(false, true)) {
                        if (configuration instanceof ElasticsearchConfigurationKnn) {
                            progressCallback.accept("[PROGRESS]:正在向量检索向知识库内容...");
                            log.info("[PROGRESS]:正在向量检索【knowEngineElasticsearchContentRetriever】知识库内容...");
                        } else if (configuration instanceof ElasticsearchConfigurationFullText) {
                            progressCallback.accept("[PROGRESS]:正在全文检索知识库内容...");
                            log.info("[PROGRESS]:正在全文检索【knowEngineElasticsearchContentRetriever】知识库内容...");
                        }
                    }
                }
                case null, default -> {
                    if (embeddingProgressSent.compareAndSet(false, true)) {
                        progressCallback.accept("[PROGRESS]:正在检索文档内容...");
                        log.info("[PROGRESS]:正在检索文档内容...");
                    }
                }
            }
        }
        //真正执行 retrieve 检索
        return delegate.retrieve(query);
    }
}
