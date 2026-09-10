package com.phuang.handler.rag;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.collect.Lists;
import com.phuang.model.constant.MetadataKeyConstant;
import com.phuang.service.KnowledgeSegmentService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.elasticsearch.*;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 *
 * @description 自定义es内容检索器
 * <p>
 * 基于 Elasticsearch 的向量检索实现，支持以下特性：
 * <ul>
 *   <li><b>向量检索 (KNN)</b>：使用 Embedding 模型将查询文本向量化，进行相似度搜索</li>
 *   <li><b>全文检索</b>：支持 Elasticsearch 全文搜索，扩展了默认检索不支持的权限过滤功能</li>
 *   <li><b>混合检索</b>：结合向量检索和全文检索（需 Elasticsearch 相应许可证）</li>
 *   <li><b>关联内容扩展</b>：自动检索兄弟分段 (brother chunk) 和父分段 (parent chunk) 内容</li>
 * </ul>
 * <p>
 * <b>关联内容扩展机制：</b>
 * <ul>
 *   <li>兄弟分段：具有相同父分段的其他子分段，用于获取完整上下文</li>
 *   <li>父分段：从 Redis 中读取父分段的完整文本，替换子分段以获得更完整的语义</li>
 * </ul>
 * <p>
 * @see ElasticsearchContentRetriever
 * @see ContentRetriever
 * @author huangpeng
 * @since 2026/9/9
 */
@Slf4j
public class KnowEngineElasticsearchContentRetriever extends AbstractElasticsearchEmbeddingStore implements ContentRetriever {

    private final EmbeddingModel embeddingModel;

    protected ElasticsearchConfiguration configuration;

    private final int maxResults;

    private final double minScore;

    private final Filter filter;

    private final KnowledgeSegmentService knowledgeSegmentService;

    /**
     * Creates an instance of ElasticsearchContentRetriever using a RestClient.
     *
     * @param configuration  Elasticsearch retriever configuration to use (knn, script, full text, hybrid, hybrid with reranker)
     * @param restClient     Elasticsearch Rest Client (mandatory)
     * @param indexName      Elasticsearch index name (optional). Default value: "default".
     *                       Index will be created automatically if not exists.
     * @param embeddingModel Embedding model to be used by the retriever
     * @param maxResults     Maximum number of results to retrieve
     * @param minScore       Minimum score threshold for retrieved results
     * @param filter         Filter to apply during retrieval
     */
    public KnowEngineElasticsearchContentRetriever(
            ElasticsearchConfiguration configuration,
            RestClient restClient,
            String indexName,
            EmbeddingModel embeddingModel,
            final int maxResults,
            final double minScore,
            final Filter filter,
            KnowledgeSegmentService knowledgeSegmentService) {
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.knowledgeSegmentService = knowledgeSegmentService;
        this.initialize(configuration, restClient, indexName);
    }

    @Override
    public List<Content> retrieve(Query query) {
        // 将查询文本转换为向量
        Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
        // 构建向量搜索请求，设置查询向量、最大返回数量、最低相似度分数和过滤条件
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();

        List<Content> searchContents;
        if (configuration instanceof ElasticsearchConfigurationFullText) {
            // 全文检索模式: 直接执行全文搜索并返回结果
            searchContents = doFullTextQuery(query);
        } else if (configuration instanceof ElasticsearchConfigurationHybrid) {
            // 混合检索模式: 结合向量检索和全文检索
            searchContents = mapResultsToContentList(this.hybridSearch(request, query.text()));
        } else {
            // 向量检索模式
            searchContents = mapResultsToContentList(this.search(request));
        }
        return proccessParentAndBrotherContent(searchContents);
    }

    /**
     * 父子分段内容处理
     * @param searchContents rag检索到的子分段内容
     * @return
     */
    @NotNull
    private List<Content> proccessParentAndBrotherContent(List<Content> searchContents) {
        // 去重并按文本内容排序
        searchContents = searchContents.stream().distinct().sorted(Comparator.comparing(content -> content.textSegment().text())).toList();
        List<Content> finalContents = Lists.newArrayList(searchContents);

        // 兄弟分段缓存和父分段缓存，避免重复查询
        Map<String, List<Content>> parentDocMap = new HashMap<>();
        Map<String, List<Content>> brotherDocMap = new HashMap<>();

        Iterator<Content> iterator = searchContents.iterator();
        for (; iterator.hasNext(); ) {
            Content content = iterator.next();
            /**
             * 兄弟分段处理: todo
             */


            /**
             * 父子分段处理:使用父分段的完整文本替换子分段,使得子分段获取更完整的语义
             */
            String parentChunkId = content.textSegment().metadata().getString(MetadataKeyConstant.PARENT_CHUNK_ID);
            if(Objects.nonNull(parentChunkId)){
                List<Content> cachedParentDocs = parentDocMap.get(parentChunkId);
                if (CollectionUtil.isNotEmpty(cachedParentDocs)) {
                    // 不为空表示已经缓存过这个父分段了,说明已经添加过了,无需重复添加
                    finalContents.remove(content);
                } else if (Objects.nonNull(knowledgeSegmentService)) {
                    //获取父分段内容
                    String segmentText = knowledgeSegmentService.getTextByChunkId(parentChunkId);
                    if(StrUtil.isNotEmpty(segmentText)){
                        // 用父分段文本构造新的 Content，替换当前的子分段内容
                        Metadata metadata = content.textSegment().metadata();
                        metadata.remove(MetadataKeyConstant.PARENT_CHUNK_ID);
                        TextSegment parentSegment = TextSegment.from(segmentText, metadata);
                        Content parentContent = Content.from(parentSegment, content.metadata());
                        List<Content> parentDocs = List.of(parentContent);
                        parentDocMap.put(parentChunkId, parentDocs);
                        finalContents.remove(content);
                        finalContents.addAll(parentDocs);
                    } else {
                        log.warn("parentChunk not found in Redis, chunkId: {}", parentChunkId);
                        finalContents.remove(content);
                    }
                }
            }
        }
        return finalContents;
    }

    /**
     * 执行全文检索查询 (默认的全文搜索不支持 filter，所以需要定制)
     * <p>
     * 根据权限过滤条件决定查询策略：
     * <ul>
     *   <li>无权限过滤（accessibleValues 为空）：使用简单的 match 查询，仅对 text 字段进行全文匹配</li>
     *   <li>有权限过滤（accessibleValues 非空）：使用 bool 查询，
     *       must 子句对 text 字段全文匹配，filter 子句通过 terms 查询限定 metadata.accessibleBy 字段，
     *       确保只返回当前用户有权访问的文档</li>
     * </ul>
     * 查询结果转换为 Content 列表，携带 SCORE 和 EMBEDDING_ID 元数据
     *
     * @param query 查询对象，包含检索文本
     * @return 带元数据的 Content 列表
     */
    @NotNull
    private List<Content> doFullTextQuery(Query query) {
        try {
            // 从 Filter 树中提取所有 IsEqualTo 的 value，用于权限过滤
            List<String> accessibleValues = extractFilterValues(filter);
            SearchResponse<Document> response = client.search(
                    s -> s.index(indexName).query(q -> accessibleValues.isEmpty() ?
                            // 无权限过滤：简单 match 查询
                            q.match(m -> m.field("text").query(query.text()))
                            // 有权限过滤：bool 查询 = must(全文匹配) + filter(权限过滤)
                            : q.bool(b -> b.must(m -> m.match(mm -> mm.field("text").query(query.text())))
                            .filter(f -> f.terms(t -> t.field("metadata.accessibleBy").terms(tv -> tv.value(accessibleValues.stream()
                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList())))))),
                    Document.class);

            // 将 ES 响应转换为 TextSegment 列表
            List<TextSegment> results = toTextList(response);
            // 将 TextSegment 转换为 Content，携带 SCORE 和 EMBEDDING_ID 元数据
            return results.stream()
                    .map(t -> Content.from(
                            t,
                            Map.of(
                                    ContentMetadata.SCORE, t.metadata().getDouble(ContentMetadata.SCORE.name()),
                                    ContentMetadata.EMBEDDING_ID,
                                    t.metadata().getString(ContentMetadata.EMBEDDING_ID.name()))))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<TextSegment> toTextList(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> document.getText() == null
                                ? null
                                : TextSegment.from(
                                document.getText(),
                                new Metadata(document.getMetadata())
                                        .put(ContentMetadata.SCORE.name(), hit.score())
                                        .put(ContentMetadata.EMBEDDING_ID.name(), hit.id())))
                        .orElse(null))
                .collect(toList());
    }

    /**
     * 递归遍历 langchain4j Filter 树，提取所有 IsEqualTo 的 value 字符串列表。
     * <p>
     * 支持 Or(IsEqualTo, ...) 结构，适配权限过滤场景。
     */
    private List<String> extractFilterValues(Filter filter) {
        List<String> values = new ArrayList<>();
        collectFilterValues(filter, values);
        return values;
    }

    private void collectFilterValues(Filter filter, List<String> values) {
        if (filter == null) {
            return;
        }
        if (filter instanceof IsEqualTo isEqualTo) {
            Object value = isEqualTo.comparisonValue();
            if (value != null) {
                values.add(value.toString());
            }
        } else if (filter instanceof Or or) {
            collectFilterValues(or.left(), values);
            collectFilterValues(or.right(), values);
        }
    }

    /**
     * 将检索结果转化为 langchain4j 的 content 集合
     * <P>
     *     直接将默认 ElasticsearchContentRetriever中的 mapResultsToContentList复制过来使用
     * </P>
     * @param searchResult
     * @return
     */
    private List<Content> mapResultsToContentList(EmbeddingSearchResult<TextSegment> searchResult) {
        List<Content> result = searchResult.matches().stream()
                .filter(f -> f.score() > minScore)
                .map(m -> Content.from(
                        m.embedded(),
                        Map.of(
                                ContentMetadata.SCORE, m.score(),
                                ContentMetadata.EMBEDDING_ID, m.embeddingId())))
                .toList();
        log.debug("Found [{}] relevant documents in Elasticsearch index [{}].", result.size(), indexName);
        return result;
    }
}
