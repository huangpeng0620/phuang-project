package com.phuang.service.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.phuang.handler.graph.GraphFactExtractor;
import com.phuang.handler.graph.Neo4jKnowledgeGraphWriter;
import com.phuang.mapper.GraphBuildTaskMapper;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.service.KnowledgeDocumentVersionService;
import com.phuang.service.KnowledgeSegmentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按文档版本编排图谱构建，并将每次尝试的结果记录在 MySQL。
 * 手动切分后的事件与 XXL-Job 共用同一个幂等入口，图谱失败不会改变文档或向量化状态。
 */
@Slf4j
@Service
public class GraphBuildService {

    @Resource
    private KnowledgeDocumentService documentService;

    @Resource
    private KnowledgeDocumentVersionService versionService;

    @Resource
    private KnowledgeSegmentService segmentService;

    @Resource
    private GraphBuildTaskMapper taskMapper;

    /**
     * LLM 事实抽取与原文校验入口
     */
    @Resource
    private GraphFactExtractor extractor;

    /**
     * Neo4j 幂等写入入口
     */
    @Resource
    private Neo4jKnowledgeGraphWriter graphWriter;

    /**
     * 在分段保存的同一 MySQL 事务中登记版本任务。
     * 数据库唯一键保证重复事件或手动补偿不会覆盖已有任务状态。
     */
    public void enqueue(Long documentId, Long versionId) {
        taskMapper.insertPendingTaskIfAbsent(documentId, versionId);
    }

    /**
     * 构建一个版本的完整事实图。领取失败表示任务不再处于待处理状态。
     * 构建失败只记录 FAILED，不自动重试。
     */
    public void build(Long versionId) {
        if (taskMapper.updatePendingTaskToRunning(versionId) == 0) {
            return;
        }
        try {
            KnowledgeDocumentVersionEntity version = versionService.getById(versionId);
            if (version == null || (version.getStatus() != DocumentStatus.CHUNKED
                    && version.getStatus() != DocumentStatus.VECTOR_STORED)) {
                throw new IllegalStateException("文档版本不存在或尚未切分: " + versionId);
            }
            KnowledgeDocumentEntity document = documentService.getById(version.getDocId());
            if (document == null || document.getKnowledgeBaseType() != KnowledgeBaseType.DOCUMENT_SEARCH) {
                throw new IllegalStateException("图谱仅处理文档搜索类型: " + versionId);
            }

            List<KnowledgeSegmentEntity> segments = segmentService.list(new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                    .eq(KnowledgeSegmentEntity::getDocumentId, document.getDocId())
                    .eq(KnowledgeSegmentEntity::getDocumentVersion, versionId)
                    .orderByAsc(KnowledgeSegmentEntity::getChunkOrder));
            if (segments.isEmpty()) {
                throw new IllegalStateException("文档版本没有可抽取的分段: " + versionId);
            }

            // 每次尝试先清掉该版本上次失败留下的部分结果，防止模型重试输出变化时产生旧事实
            graphWriter.startVersion(document, version);
            for (KnowledgeSegmentEntity segment : segments) {
                // SMART 切分中的父分段已由子分段覆盖；只抽正文，避免同一事实重复调用模型
                if (Integer.valueOf(1).equals(segment.getSkipEmbedding())
                        || segment.getText() == null || segment.getText().isBlank()) {
                    continue;
                }
                List<GraphFactExtractor.Fact> facts = extractor.extract(segment);
                graphWriter.writeSegment(segment, facts);
            }
            graphWriter.completeVersion(versionId);
            taskMapper.updateRunningTaskToSucceeded(versionId);
            log.info("文档图谱构建完成, docId={}, versionId={}, segments={}", document.getDocId(), versionId, segments.size());
        } catch (Exception e) {
            // 异常消息可能携带内部文档或模型响应，只记录异常类型
            taskMapper.updateRunningTaskToFailed(versionId, e.getClass().getSimpleName());
            log.error("文档图谱构建失败, versionId={}, type={}", versionId, e.getClass().getSimpleName());
        }
    }

    /**
     * 返回一批待处理任务 ID，供定时任务逐个处理
     * @param limit
     * @return
     */
    public List<Long> pendingVersionIds(int limit) {
        return taskMapper.selectPendingVersionIds(limit);
    }

    /**
     * 为现有当前版本补登构图任务；只处理已经切分且尚无任务的文档。
     * 不重新上传原文件，也不改动其向量状态。
     */
    public void enqueueMissingCurrentVersions(int limit) {
        for (Long versionId : taskMapper.selectCurrentSegmentedDocumentSearchVersionIdsWithoutTask(limit)) {
            KnowledgeDocumentVersionEntity version = versionService.getById(versionId);
            if (version != null) {
                enqueue(version.getDocId(), versionId);
            }
        }
    }
}
