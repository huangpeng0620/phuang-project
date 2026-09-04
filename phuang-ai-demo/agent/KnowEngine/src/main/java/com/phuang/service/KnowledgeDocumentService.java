package com.phuang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;

import java.util.List;

/**
 *
 * @description 知识文档表 Service 接口
 * @author huangpeng
 * @since 2026/8/29
 */

public interface KnowledgeDocumentService extends IService<KnowledgeDocumentEntity> {

    Boolean advanceDocumentAndVersionStatus(Long docId, Long version, DocumentStatus targetStatus);

    void activateVersion(Long versionId);

    void deactivateVersion(Long versionId);

    List<KnowledgeDocumentEntity> scanDocumentsNeedingCleanup();
}
