package com.phuang.service.vector;

import com.phuang.model.entity.KnowledgeSegmentEntity;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 *
 * @description VectorStoreService
 * @author huangpeng
 * @since 2026/9/1
 */

public interface VectorStoreService {

    List<String> embedAndStore(List<KnowledgeSegmentEntity> segments);

    String embedAndStore(KnowledgeSegmentEntity segment);

    void remove(String embeddingId);

    void removeAll(List<String> embeddingIds);

    TextSegment toTextSegment(KnowledgeSegmentEntity segment);

    void removeByDocIdAndVersion(Long docId, Long versionId);
}
