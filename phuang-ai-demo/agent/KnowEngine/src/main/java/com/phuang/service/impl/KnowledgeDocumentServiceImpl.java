package com.phuang.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.KnowledgeDocumentMapper;
import com.phuang.mapper.KnowledgeSegmentMapper;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.SegmentStatus;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.service.KnowledgeDocumentVersionService;
import com.phuang.service.KnowledgeSegmentService;
import com.phuang.service.vector.VectorStoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Iterator;
import java.util.List;

/**
 *
 * @description KnowledgeDocumentServiceImpl
 * @author huangpeng
 * @since 2026/8/29
 */
@Service
@Slf4j
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentEntity> implements KnowledgeDocumentService {

    @Resource
    private KnowledgeDocumentVersionService knowledgeDocumentVersionService;

    @Resource
    private KnowledgeSegmentService knowledgeSegmentService;

    @Resource
    private VectorStoreService vectorStoreService;

    @Resource
    private KnowledgeSegmentMapper knowledgeSegmentMapper;

    /**
     * 更新版本状态
     * @param docId 文档ID
     * @param version 版本号
     * @param targetStatus 目标状态
     * @return
     */
    @Override
    @Transactional
    public Boolean advanceDocumentAndVersionStatus(Long docId, Long version, DocumentStatus targetStatus) {
        KnowledgeDocumentEntity document = this.getById(docId);
        Assert.notNull(document, "文档不存在: docId=" + docId);

        KnowledgeDocumentVersionEntity documentVersionEntity = knowledgeDocumentVersionService.getById(version);
        Assert.notNull(documentVersionEntity, "版本记录不存在: versionId=" + version);
        Assert.isTrue(docId.equals(documentVersionEntity.getDocId()), "版本不属于该文档");

        Boolean updated = Boolean.FALSE;
        //更新文档状态
        if (shouldAdvanceStatus(document.getStatus(), targetStatus)) {
            document.setStatus(targetStatus);
            this.updateById(document);
            updated = Boolean.TRUE;
            log.info("文档状态已推进, docId={}, status={}", docId, targetStatus);
        } else {
            log.info("文档状态无需推进, docId={}, currentStatus={}, targetStatus={}", docId, document.getStatus(), targetStatus);
        }

        //更新文档版本状态
        if (shouldAdvanceStatus(documentVersionEntity.getStatus(), targetStatus)) {
            documentVersionEntity.setStatus(targetStatus);
            knowledgeDocumentVersionService.updateById(documentVersionEntity);
            updated = true;
            log.info("版本状态已推进, versionId={}, status={}", version, targetStatus);
        } else {
            log.info("版本状态无需推进, versionId={}, currentStatus={}, targetStatus={}", version, documentVersionEntity.getStatus(), targetStatus);
        }
        return updated;
    }

    /**
     * 让指定版本生效（重新向量化）：
     * 1. 校验版本状态必须为 CHUNKED
     * 2. 对该版本下所有 STORED 且未向量化的分段分批 embed 并写入 ES
     * 3. 更新分段状态为 VECTOR_STORED
     * 4. 将版本记录状态从 CHUNKED 升为 VECTOR_STORED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateVersion(Long versionId) {
        KnowledgeDocumentVersionEntity documentVersionEntity = knowledgeDocumentVersionService.getById(versionId);
        Assert.notNull(documentVersionEntity, "版本记录不存在: versionId=" + versionId);
        if (documentVersionEntity.getStatus() == DocumentStatus.VECTOR_STORED) {
            return;
        }
        Assert.isTrue(DocumentStatus.CHUNKED == documentVersionEntity.getStatus(), "版本状态不是 CHUNKED,无法执行生效操作,当前状态: " + documentVersionEntity.getStatus());
        Long docId = documentVersionEntity.getDocId();
        log.info("开始让版本生效（重新向量化）,docId={},versionId={}", docId, versionId);

        //  分页扫描 STORED 且未向量化的分段（skipEmbedding = 0）
        LambdaQueryWrapper<KnowledgeSegmentEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                .eq(KnowledgeSegmentEntity::getDocumentId, docId)
                .eq(KnowledgeSegmentEntity::getDocumentVersion, versionId)
                .eq(KnowledgeSegmentEntity::getStatus, SegmentStatus.STORED)
                .eq(KnowledgeSegmentEntity::getSkipEmbedding, 0)
                .isNull(KnowledgeSegmentEntity::getEmbeddingId);
        Page<KnowledgeSegmentEntity> page = knowledgeSegmentService.page(new Page<>(1, 100), queryWrapper);
        while (!page.getRecords().isEmpty()) {
            List<KnowledgeSegmentEntity> batch = page.getRecords();
            //向量化存储
            List<String> embeddingIds = vectorStoreService.embedAndStore(batch);
            for (int i = 0; i < batch.size(); i++) {
                KnowledgeSegmentEntity seg = batch.get(i);
                // 更新文档分块向量ID和状态【向量存储完成】
                seg.setEmbeddingId(embeddingIds.get(i));
                seg.setStatus(SegmentStatus.VECTOR_STORED);
                knowledgeSegmentService.updateById(seg);
            }
            page = knowledgeSegmentService.page(new Page<>(page.getCurrent(), 100), queryWrapper);
        }

        //更新文档版本状态至【向量存储完成】
        documentVersionEntity.setStatus(DocumentStatus.VECTOR_STORED);
        knowledgeDocumentVersionService.updateById(documentVersionEntity);
    }

    /**
     * 让指定版本失效：
     * 1. 按 docId + versionId 清理 ES 向量
     * 2. 将该版本下所有分段状态从 VECTOR_STORED 降为 STORED，并清空 embeddingId
     * 3. 将版本记录状态从 VECTOR_STORED 降为 CHUNKED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivateVersion(Long versionId) {
        KnowledgeDocumentVersionEntity documentVersionEntity = knowledgeDocumentVersionService.getById(versionId);
        Assert.notNull(documentVersionEntity, "版本记录不存在: versionId=" + versionId);
        if (documentVersionEntity.getStatus() == DocumentStatus.CHUNKED) {
            return;
        }
        Assert.isTrue(DocumentStatus.VECTOR_STORED == documentVersionEntity.getStatus(), "版本状态不是 VECTOR_STORED,无法执行失效操作,当前状态: " + documentVersionEntity.getStatus());
        Long docId = documentVersionEntity.getDocId();
        log.info("开始让版本失效, docId={}, versionId={}", docId, versionId);

        // 按 docId + versionId 清理 ES 向量
        vectorStoreService.removeByDocIdAndVersion(docId, versionId);

        // 将该版本下所有分段状态从 VECTOR_STORED 降为 STORED，并清空 embeddingId
        LambdaUpdateWrapper<KnowledgeSegmentEntity> segUpdate = new LambdaUpdateWrapper<KnowledgeSegmentEntity>()
                .set(KnowledgeSegmentEntity::getStatus, SegmentStatus.STORED)
                .set(KnowledgeSegmentEntity::getEmbeddingId, null)
                .eq(KnowledgeSegmentEntity::getDocumentId, docId)
                .eq(KnowledgeSegmentEntity::getDocumentVersion, versionId)
                .eq(KnowledgeSegmentEntity::getStatus, SegmentStatus.VECTOR_STORED);
        int affected = knowledgeSegmentMapper.update(null, segUpdate);
        log.info("降级分段状态完成, versionId={}, affected={}", versionId, affected);

        // 将版本记录状态从 VECTOR_STORED 降为 CHUNKED
        documentVersionEntity.setStatus(DocumentStatus.CHUNKED);
        knowledgeDocumentVersionService.updateById(documentVersionEntity);
    }

    /**
     * 获取所有可能需要清理的文档
     * @return
     */
    @Override
    public List<KnowledgeDocumentEntity> scanDocumentsNeedingCleanup() {
        // 查询所有状态为 VECTOR_STORED 且有 currentVersionId 的文档
        List<KnowledgeDocumentEntity> documents = this.list(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getStatus, DocumentStatus.VECTOR_STORED.name())
                .isNotNull(KnowledgeDocumentEntity::getCurrentVersionId));
        if (CollectionUtil.isEmpty(documents)) {
            return Lists.newArrayList();
        }
        List<KnowledgeDocumentEntity> result = Lists.newArrayList();
        for (KnowledgeDocumentEntity document : documents) {
            long count = knowledgeSegmentService.count(new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                    .eq(KnowledgeSegmentEntity::getDocumentId, document.getDocId())
                    .ne(KnowledgeSegmentEntity::getDocumentVersion, document.getCurrentVersionId()));
            if (count > 0) {
                result.add(document);
            }
        }
        return result;
    }

    /**
     * 判断状态是否需要推进 (当前状态为空或按枚举声明顺序早于目标状态时才允许推进)
     * @param current 当前状态
     * @param target  目标状态
     * @return
     */
    private boolean shouldAdvanceStatus(DocumentStatus current, DocumentStatus target) {
        if (current == null) {
            return true;
        }
        return current.ordinal() < target.ordinal();
    }
}
