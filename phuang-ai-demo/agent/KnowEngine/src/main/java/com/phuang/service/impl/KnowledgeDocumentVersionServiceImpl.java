package com.phuang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.KnowledgeDocumentVersionMapper;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.service.KnowledgeDocumentVersionService;
import com.phuang.util.VersionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 文档版本表 Service 实现
 *
 * @author huangpeng
 * @since 2026/8/29
 */
@Service
@Slf4j
public class KnowledgeDocumentVersionServiceImpl extends ServiceImpl<KnowledgeDocumentVersionMapper, KnowledgeDocumentVersionEntity> implements KnowledgeDocumentVersionService {


    /**
     * 语义化版本比较器（按 major.minor.patch 数值比较）
     */
    private static final Comparator<KnowledgeDocumentVersionEntity> VERSION_COMPARATOR = Comparator.comparing(KnowledgeDocumentVersionEntity::getVersion, VersionUtil::compareVersions);

    /**
     * 通过hash值判断是否存在同文档
     *
     * @param contentHash
     * @return
     */
    @Override
    public boolean existsByContentHash(String contentHash) {
        return count(new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                .eq(KnowledgeDocumentVersionEntity::getContentHash, contentHash)) > 0;
    }

    /**
     * 根据 docId 获取文档版本列表(按照语义版本号排序)
     * @param docId
     * @return
     */
    @Override
    public List<KnowledgeDocumentVersionEntity> listByDocId(Long docId) {
        List<KnowledgeDocumentVersionEntity> versions = list(new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                .eq(KnowledgeDocumentVersionEntity::getDocId, docId));
        versions.sort(VERSION_COMPARATOR.reversed());
        return versions;
    }

    /**
     * 获取最新文档版本记录
     *
     * @param docId
     * @return
     */
    @Override
    public String getLatestVersion(Long docId) {
        List<KnowledgeDocumentVersionEntity> versions = listByDocId(docId);
        if (versions.isEmpty()) {
            return null;
        }
        return versions.iterator().next().getVersion();
    }
}
