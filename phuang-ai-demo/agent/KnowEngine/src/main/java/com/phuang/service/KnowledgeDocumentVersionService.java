package com.phuang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;

import java.util.List;

/**
 * 文档版本表 Service 接口
 *
 * @author huangpeng
 * @since 2026/8/29
 */
public interface KnowledgeDocumentVersionService extends IService<KnowledgeDocumentVersionEntity> {

    boolean existsByContentHash(String contentHash);

    List<KnowledgeDocumentVersionEntity> listByDocId(Long docId);

    String getLatestVersion(Long docId);
}
