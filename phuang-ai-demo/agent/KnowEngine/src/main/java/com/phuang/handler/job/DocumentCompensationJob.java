package com.phuang.handler.job;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.service.KnowledgeDocumentVersionService;
import com.phuang.service.document.DocumentProcessService;
import com.phuang.service.vector.VectorStoreService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档处理补偿任务
 * 用于处理事件处理失败后的补偿逻辑
 */
@Slf4j
@Component
public class DocumentCompensationJob {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private KnowledgeDocumentVersionService knowledgeDocumentVersionService;

    @Resource
    private DocumentProcessService documentProcessService;

    @Resource
    private VectorStoreService vectorStoreService;

    @Value("${document.upload-compensation.delay-minutes:10}")
    private long uploadCompensationDelayMinutes;

    @Value("${document.upload-compensation.batch-size:100}")
    private long uploadCompensationBatchSize;

    /**
     * 向量化补偿任务
     * 扫描 CHUNKED 状态但存在未向量化的 segment，重新触发向量化
     */
    @XxlJob("documentEmbeddingCompensation")
    public void documentEmbeddingCompensation() {
        log.info("========== 开始执行向量化补偿任务 ==========");
        int successCount = 0;
        int failCount = 0;

        try {
            // 查询 CHUNKED 状态的文档
            List<KnowledgeDocumentVersionEntity> documents = knowledgeDocumentVersionService.list(new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                    .eq(KnowledgeDocumentVersionEntity::getStatus, DocumentStatus.CHUNKED));
            log.info("发现 {} 个 CHUNKED 状态的文档", documents.size());
            if (CollectionUtil.isEmpty(documents)) {
                return;
            }

            for (KnowledgeDocumentVersionEntity documentVersion : documents) {
                KnowledgeDocumentEntity document = knowledgeDocumentService.getById(documentVersion.getDocId());
                if (!document.getCurrentVersionId().equals(documentVersion.getVersionId())) {
                    log.warn("文档 {} 当前版本 {} 不匹配，跳过补偿", documentVersion.getDocId(), documentVersion.getVersion());
                    continue;
                }
                try {
                    // 执行向量化
                    boolean success = documentProcessService.embedAndStore(documentVersion);
                    if (success) {
                        // 更新重试次数
                        log.info("向量化补偿成功，documentId: {} , version: {}", documentVersion.getDocId(), documentVersion.getVersion());
                        successCount++;
                    } else {
                        log.warn("向量化补偿失败，documentId: {} , version: {}", documentVersion.getDocId(), documentVersion.getVersion());
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("向量化补偿失败，documentId: {} , version: {}", documentVersion.getDocId(), documentVersion.getVersion(), e);
                    failCount++;
                }
            }
        } catch (Exception e) {
            log.error("向量化补偿任务执行异常", e);
        }

        log.info("========== 向量化补偿任务完成，成功: {}，失败: {} ==========", successCount, failCount);
    }

    /**
     * 上传文档异步处理补偿任务。
     *
     * <p>补偿事件丢失、转换异常和最终信息回写失败的版本。通过延迟窗口避开仍在正常执行的
     * 异步监听器，具体处理过程与 onDocumentUploaded 共用同一个加锁业务入口。</p>
     */
    @XxlJob("documentUploadCompensation")
    public void documentUploadCompensation() {
        long delayMinutes = Math.max(uploadCompensationDelayMinutes, 1L);
        long batchSize = Math.max(uploadCompensationBatchSize, 1L);
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(delayMinutes);
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        log.info("========== 开始执行上传文档补偿任务，延迟窗口: {} 分钟，批次大小: {} ==========", delayMinutes, batchSize);
        try {
            LambdaQueryWrapper<KnowledgeDocumentVersionEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                    .and(statusWrapper -> statusWrapper.in(KnowledgeDocumentVersionEntity::getStatus,
                                    DocumentStatus.UPLOADED, DocumentStatus.CONVERTING)
                            .or(convertedWrapper -> convertedWrapper
                                    .in(KnowledgeDocumentVersionEntity::getStatus,
                                            DocumentStatus.CONVERTED, DocumentStatus.STORED)
                                    .and(urlWrapper -> urlWrapper
                                            .isNull(KnowledgeDocumentVersionEntity::getConvertedDocUrl)
                                            .or()
                                            .eq(KnowledgeDocumentVersionEntity::getConvertedDocUrl, ""))))
                    .lt(KnowledgeDocumentVersionEntity::getUpdatedAt, expirationTime)
                    .orderByAsc(KnowledgeDocumentVersionEntity::getUpdatedAt);

            List<KnowledgeDocumentVersionEntity> candidates = knowledgeDocumentVersionService.page(new Page<>(1, batchSize, false), queryWrapper).getRecords();
            if (CollectionUtil.isEmpty(candidates)) {
                log.info("没有发现需要补偿的上传文档");
                return;
            }
            log.info("发现 {} 个需要补偿的文档版本", candidates.size());
            for (KnowledgeDocumentVersionEntity documentVersion : candidates) {
                try {
                    //完成上传文档的转换和数据库回写
                    boolean success = documentProcessService.completeUploadedDocumentProcessing(documentVersion.getDocId(), documentVersion.getVersionId());
                    if (success) {
                        successCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.error("上传文档补偿失败, documentId={}, versionId={}", documentVersion.getDocId(), documentVersion.getVersionId(), e);
                }
            }
        } catch (Exception e) {
            log.error("上传文档补偿任务执行异常", e);
        }
        log.info("========== 上传文档补偿任务完成，成功: {}，跳过: {}，失败: {} ==========", successCount, skippedCount, failedCount);
    }

    /**
     * 扫描所有状态为 VECTOR_STORED 的文档，检查是否存在旧版本残留分段，
     */
    @XxlJob("retryFailedCleanups")
    public void retryFailedCleanups() {
        try {
            List<KnowledgeDocumentEntity> docsToCleanup = knowledgeDocumentService.scanDocumentsNeedingCleanup();
            if (docsToCleanup.isEmpty()) {
                return;
            }
            log.info("定时任务发现 {} 个文档需要清理旧版本数据", docsToCleanup.size());
            for (KnowledgeDocumentEntity docInfo : docsToCleanup) {
                //根据【文档ID + 文档当前生效的版本ID】清除向量旧版本记录
                vectorStoreService.removeByDocIdAndVersion(docInfo.getDocId(), docInfo.getCurrentVersionId());
            }
        } catch (Exception e) {
            log.error("定时清理任务执行异常: {}", e.getMessage(), e);
        }
    }
}
