package com.phuang.service.vector;

import com.phuang.model.constant.MetadataKeyConstant;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 *
 * @description 向量存储服务类(基于ES)
 * @author huangpeng
 * @since 2026/9/1
 */
@Service
@Slf4j
public class VectorStoreServiceImpl implements VectorStoreService {

    @Resource
    private ElasticsearchEmbeddingStore embeddingStore;

    @Resource
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    /**
     * 批量向量化并存储
     * @param segments 文档分片
     * @return
     */
    @Override
    public List<String> embedAndStore(List<KnowledgeSegmentEntity> segments) {
        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }
        List<TextSegment> textSegments = segments.stream().map(this::toTextSegment).toList();
        Response<List<Embedding>> embeddingResponse = openAiEmbeddingModel.embedAll(textSegments);
        List<String> embeddingIds = embeddingStore.addAll(embeddingResponse.content(), textSegments);
        Assert.isTrue(embeddingIds.size() == segments.size(), "向量存储失败,向量数量与分段数量不一致");
        log.info("批量向量化完成, count: {}", segments.size());
        return embeddingIds;
    }

    /**
     * 单条向量化并存储
     * @param segment 文档分片
     * @return
     */
    @Override
    public String embedAndStore(KnowledgeSegmentEntity segment) {
        Assert.notNull(segment, "分段不能为空");
        TextSegment textSegment = toTextSegment(segment);
        Response<Embedding> embeddingResponse = openAiEmbeddingModel.embed(textSegment.text());
        String embeddingId = embeddingStore.add(embeddingResponse.content(), textSegment);
        log.info("单条向量化完成,segmentId: {}, embeddingId:{}", segment.getId(), embeddingId);
        return embeddingId;
    }

    /**
     * 删除向量
     * @param embeddingId
     */
    @Override
    public void remove(String embeddingId) {
        if (embeddingId == null) {
            return;
        }
        try {
            embeddingStore.remove(embeddingId);
            log.info("删除向量成功, embeddingId: {}", embeddingId);
        } catch (Exception e) {
            log.warn("删除向量失败, embeddingId: {}, error: {}", embeddingId, e.getMessage());
        }
    }

    /**
     * 批量删除向量
     * @param embeddingIds
     */
    @Override
    public void removeAll(List<String> embeddingIds) {
        if (CollectionUtils.isEmpty(embeddingIds)) {
            return;
        }
        try {
            embeddingStore.removeAll(embeddingIds);
            log.info("批量删除向量成功, count: {}", embeddingIds.size());
        } catch (Exception e) {
            log.warn("批量删除向量失败, count: {}, error: {}", embeddingIds.size(), e.getMessage());
        }
    }

    @Override
    public void removeByDocIdAndVersion(Long docId, Long versionId) {
        try {
            Filter filter = metadataKey(MetadataKeyConstant.DOC_ID).isEqualTo(docId).and(metadataKey(MetadataKeyConstant.VERSION).isEqualTo(versionId));
            embeddingStore.removeAll(filter);
            log.info("按 docId + versionId 删除向量成功,docId:{},versionId:{}", docId, versionId);
        } catch (Exception e) {
            log.error("按 docId + versionId 删除向量失败,docId:{},versionId:{},error:{}", docId, versionId, e.getMessage());
        }
    }

    @Override
    public TextSegment toTextSegment(KnowledgeSegmentEntity segment) {
        Map<String, String> metadataMap = segment.getMetadataMap();
        Metadata metadata = metadataMap != null ? Metadata.from(metadataMap) : new Metadata();
        return TextSegment.from(segment.getText(), metadata);
    }
}
