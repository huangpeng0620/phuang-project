package com.phuang.service.document;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.phuang.handler.event.DocumentChunkedEvent;
import com.phuang.handler.splitter.DocumentSplitterFactory;
import com.phuang.handler.splitter.ExcelSplitter;
import com.phuang.hlock.annotation.HLock;
import com.phuang.model.constant.MetadataKeyConstant;
import com.phuang.model.dto.DocumentSplitParam;
import com.phuang.model.dto.DocumentUploadParam;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.model.enums.SegmentStatus;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.*;
import com.phuang.service.impl.FileStorageService;
import com.phuang.util.FileTypeUtil;
import com.phuang.util.MinioObjectNameUtil;
import com.phuang.util.VersionUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 *
 * @description DocumentProcessServiceImpl
 * @author huangpeng
 * @since 2026/8/30
 */
@Slf4j
@Service
public class DocumentProcessServiceImpl implements DocumentProcessService {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private KnowledgeDocumentVersionService knowledgeDocumentVersionService;

    @Resource
    private KnowledgeSegmentService knowledgeSegmentService;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private FileProcessServiceFactory fileProcessServiceFactory;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucketName}")
    private String bucketName;

    /**
     * 文件上传
     * @param documentUploadParam
     * @param uploadUser
     */
    @Transactional(rollbackFor = Exception.class)
    @HLock(prefixKey = "document_upload", key = "#uploadUser", waitTime = 0)
    @Override
    public Boolean upload(DocumentUploadParam documentUploadParam, String uploadUser) throws Exception {
        // 计算文件内容hash，用于去重
        String contentHash = calculateContentHash(documentUploadParam.file());
        // 检查是否已存在相同内容的版本（跨文档跨版本去重）
        if (knowledgeDocumentVersionService.existsByContentHash(contentHash)) {
            throw new BusinessException("文档内容已存在,请勿重复上传");
        }

        //文件存储到 minio
        String fileName = documentUploadParam.file().getOriginalFilename();
        String objectName = MinioObjectNameUtil.generateObjectName(fileName);
        String docUrl = CharSequenceUtil.EMPTY;
        try {
            docUrl = fileStorageService.uploadFile(documentUploadParam.file(), objectName);
            log.info("文件上传成功,originalFileName:{},objectName:{}", fileName, objectName);
        } catch (Exception e) {
            log.error("文件上传失败,originalFileName:{},objectName:{}", fileName, objectName, e);
            return Boolean.FALSE;
        }

        //构建并保存文档记录
        KnowledgeDocumentEntity knowledgeDocumentEntity = new KnowledgeDocumentEntity().create(documentUploadParam);
        knowledgeDocumentService.save(knowledgeDocumentEntity);

        //构建并保存文档版本记录
        KnowledgeDocumentVersionEntity documentVersionEntity = KnowledgeDocumentVersionEntity.builder()
                .docId(knowledgeDocumentEntity.getDocId())
                .docUrl(docUrl)
                .convertedDocUrl(CharSequenceUtil.EMPTY)
                .contentHash(contentHash)
                .uploadUser(uploadUser)
                .status(DocumentStatus.UPLOADED)
                .version(documentUploadParam.version())
                .build();
        knowledgeDocumentVersionService.save(documentVersionEntity);
        knowledgeDocumentEntity.setCurrentVersionId(documentVersionEntity.getVersionId());

        // 处理文档（转换/存储），获取转换后的文档 URL
        String convertedDocUrl = processFile(fileName, documentUploadParam.file(), knowledgeDocumentEntity, docUrl);

        //更新文档版本记录中转换后的文档 URL
        documentVersionEntity = knowledgeDocumentVersionService.getById(documentVersionEntity.getVersionId());
        documentVersionEntity.setConvertedDocUrl(convertedDocUrl);
        knowledgeDocumentVersionService.updateById(documentVersionEntity);

        //更新文档版本ID
        knowledgeDocumentEntity = knowledgeDocumentService.getById(knowledgeDocumentEntity.getDocId());
        knowledgeDocumentEntity.setCurrentVersionId(documentVersionEntity.getVersionId());
        knowledgeDocumentService.updateById(knowledgeDocumentEntity);
        return Boolean.TRUE;
    }

    @Override
    public Boolean uploadNewVersion(Long docId, String version, MultipartFile file, String uploadUser, String changelog) throws Exception {
        KnowledgeDocumentEntity document = knowledgeDocumentService.getById(docId);
        Assert.notNull(document, "文档不存在");

        // 校验版本号必须大于已有最大版本号
        String latestVersion = knowledgeDocumentVersionService.getLatestVersion(docId);
        if (latestVersion != null && VersionUtil.compareVersions(version, latestVersion) <= 0) {
            throw new BusinessException("版本号 " + version + " 不大于现有最新版本号 " + latestVersion + "，请使用更大的版本号");
        }

        // 计算文件内容hash，用于去重
        String contentHash = calculateContentHash(file);

        // 检查是否已存在相同内容的版本（跨文档跨版本去重）
        if (knowledgeDocumentVersionService.existsByContentHash(contentHash)) {
            throw new BusinessException("文档内容已存在，请勿重复上传");
        }

        //上传新版本文件到 MinIO（不清理旧版本数据）
        String fileName = file.getOriginalFilename();
        String objectName = MinioObjectNameUtil.generateObjectName(fileName);
        String fileUrl = null;
        try {
            fileUrl = fileStorageService.uploadFile(file, objectName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 创建新版本记录
        KnowledgeDocumentVersionEntity documentVersionEntity = KnowledgeDocumentVersionEntity.builder()
                .docId(document.getDocId())
                .docUrl(fileUrl)
                .version(version)
                .changelog(changelog)
                .convertedDocUrl(CharSequenceUtil.EMPTY)
                .contentHash(contentHash)
                .uploadUser(uploadUser)
                .status(DocumentStatus.UPLOADED)
                .build();
        knowledgeDocumentVersionService.save(documentVersionEntity);
        //是不是不能更新这个字段，需要手动进行文档版本切换才行？？？ todo
        document.setCurrentVersionId(documentVersionEntity.getVersionId());

        // 处理文档（转换/存储）,获取转换后的文档URL
        String convertedDocUrl = processFile(fileName, file, document, fileUrl);

        documentVersionEntity = knowledgeDocumentVersionService.getById(documentVersionEntity.getVersionId());
        documentVersionEntity.setConvertedDocUrl(convertedDocUrl);

        knowledgeDocumentVersionService.updateById(documentVersionEntity);
        knowledgeDocumentService.updateById(document);
        return Boolean.TRUE;
    }

    /**
     * 处理文档（转换/存储）
     */
    private String processFile(String fileName, MultipartFile file, KnowledgeDocumentEntity document, String fileUrl) throws Exception {
        String convertedDocUrl;
        FileProcessService fileProcessService = fileProcessServiceFactory.get(FileTypeUtil.getFileType(fileName, file), document.getKnowledgeBaseType());
        if (fileProcessService != null) {
            convertedDocUrl = fileProcessService.processDocument(document, fileUrl, file.getInputStream());
        } else {
            DocumentStatus targetStatus = document.getKnowledgeBaseType() == KnowledgeBaseType.DOCUMENT_SEARCH ? DocumentStatus.CONVERTED : DocumentStatus.STORED;
            //更新文档状态【转换完成｜存储完成】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), targetStatus);
            document.setStatus(targetStatus);
            convertedDocUrl = fileUrl;
        }
        return convertedDocUrl;
    }

    /**
     * 文档分块(不包含向量化)
     * @param documentSplitParam
     * @return
     */
    @Override
    public Integer split(DocumentSplitParam documentSplitParam) {
        KnowledgeDocumentEntity documentEntity = knowledgeDocumentService.getById(documentSplitParam.documentId());
        Assert.notNull(documentEntity, "文档不存在");

        KnowledgeDocumentVersionEntity documentVersionEntity = knowledgeDocumentVersionService.getById(documentEntity.getCurrentVersionId());
        Assert.notNull(documentVersionEntity, "文档版本不存在");
        Assert.notNull(documentVersionEntity.getConvertedDocUrl(), "文档未转换完成");

        if (documentVersionEntity.getStatus() == DocumentStatus.CHUNKED) {
            // 返回已切分的分段数量（仅统计当前版本的分段）
            return (int) knowledgeSegmentService.count(new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                    .eq(KnowledgeSegmentEntity::getDocumentId, documentEntity.getDocId())
                    .eq(KnowledgeSegmentEntity::getDocumentVersion, documentEntity.getCurrentVersionId())
                    .eq(KnowledgeSegmentEntity::getSkipEmbedding, 0));
        }
        if (documentVersionEntity.getStatus() != DocumentStatus.CONVERTED) {
            throw new BusinessException("文档状态不为CONVERTED，无法完成切分");
        }

        // minio文件下载
        String convertedDocUrl = documentVersionEntity.getConvertedDocUrl();
        String objectName = extractObjectNameFromUrl(convertedDocUrl);
        Assert.notNull(objectName, "无法解析文档URL");

        List<TextSegment> textSegmentList = Lists.newArrayList();
        try (InputStream inputStream = fileStorageService.downloadFile(objectName)) {
            // EXCEL 单独处理，因为他不是Document类型
            if (FileType.EXCEL == FileTypeUtil.getFileType(convertedDocUrl) || FileType.CSV == FileTypeUtil.getFileType(convertedDocUrl)) {
                ExcelSplitter splitter = new ExcelSplitter(documentSplitParam.chunkSize(), false);
                textSegmentList.addAll(splitter.split(inputStream.readAllBytes()));
            } else {
                DocumentSplitter splitter = DocumentSplitterFactory.getInstance(documentSplitParam);
                Document doc = Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                textSegmentList.addAll(splitter.split(doc));
            }
        } catch (Exception e) {
            throw new RuntimeException("下载文档失败: " + e.getMessage(), e);
        }

        // 4. 转换为 KnowledgeSegment 并保存
        List<KnowledgeSegmentEntity> knowledgeSegmentEntityList = Lists.newArrayList();
        for (int i = 0; i < textSegmentList.size(); i++) {
            TextSegment segment = textSegmentList.get(i);
            KnowledgeSegmentEntity knowledgeSegmentEntity = KnowledgeSegmentEntity.builder()
                    .text(segment.text())
                    .chunkId(segment.metadata().getString(MetadataKeyConstant.CHUNK_ID))
                    .documentId(documentEntity.getDocId())
                    .metadata(enrichMetadata(documentEntity, documentVersionEntity, segment.metadata()))
                    .documentVersion(documentEntity.getCurrentVersionId())
                    .status(SegmentStatus.STORED)
                    .chunkOrder(i)
                    .build();
            // 检查是否需要跳过嵌入
            Integer skipEmbedding = segment.metadata().getInteger(MetadataKeyConstant.SKIP_EMBEDDING);
            knowledgeSegmentEntity.setSkipEmbedding(skipEmbedding != null && skipEmbedding == 1 ? 1 : 0);
            if (skipEmbedding != null && skipEmbedding == 1) {
                knowledgeSegmentEntity.setSkipEmbedding(1);
                knowledgeSegmentEntity.setStatus(SegmentStatus.STORED);
            } else {
                knowledgeSegmentEntity.setSkipEmbedding(0);
                knowledgeSegmentEntity.setStatus(SegmentStatus.STORED);
            }
            knowledgeSegmentEntityList.add(knowledgeSegmentEntity);
        }
        knowledgeSegmentService.saveBatch(knowledgeSegmentEntityList);

        //更新文档状态至【分块完成】
        knowledgeDocumentService.advanceDocumentAndVersionStatus(documentEntity.getDocId(), documentEntity.getCurrentVersionId(), DocumentStatus.CHUNKED);

        //发送文档已分段事件
        DocumentChunkedEvent event = new DocumentChunkedEvent(this, documentEntity.getDocId(), documentEntity.getCurrentVersionId(), knowledgeSegmentEntityList.size());
        eventPublisher.publishEvent(event);

        return knowledgeSegmentEntityList.size();
    }

    /**
     * 切换文档到指定版本
     * @param docId
     * @param versionId
     */
    @Override
    public void switchVersion(Long docId, Long versionId) {
        KnowledgeDocumentEntity documentEntity = knowledgeDocumentService.getById(docId);
        Assert.notNull(documentEntity, "文档不存在");
        KnowledgeDocumentVersionEntity documentVersionEntity = knowledgeDocumentVersionService.getById(versionId);
        Assert.notNull(documentVersionEntity, "文档版本不存在");
        if (documentEntity.getCurrentVersionId().equals(versionId)) {
            log.info("文档已经是指定版本,无需切换");
            return;
        }
        log.info("切换文档 {} 的版本:从 versionId = {} 切换到 versionId ={}", docId, documentEntity.getCurrentVersionId(), versionId);

        // DATA_QUERY 类型文档无向量,直接切换即可
        if (documentEntity.getKnowledgeBaseType() == KnowledgeBaseType.DATA_QUERY) {
            String latestVersion = knowledgeDocumentVersionService.getLatestVersion(docId);
            if (latestVersion != null && VersionUtil.compareVersions(documentVersionEntity.getVersion(), latestVersion) < 0) {
                throw new IllegalArgumentException("DATA_QUERY 类型文档不支持切换到旧版本");
            }
            documentEntity.setCurrentVersionId(versionId);
            knowledgeDocumentService.updateById(documentEntity);
            return;
        }

        // 更新原版本文档片段状态为 STORED
        LambdaUpdateWrapper<KnowledgeSegmentEntity> updateWrapper = new LambdaUpdateWrapper<KnowledgeSegmentEntity>()
                .set(KnowledgeSegmentEntity::getStatus, SegmentStatus.STORED)
                .eq(KnowledgeSegmentEntity::getDocumentId, documentEntity.getDocId())
                .eq(KnowledgeSegmentEntity::getDocumentVersion, documentEntity.getCurrentVersionId());
        knowledgeSegmentService.update(updateWrapper);

        //向量化存储
        embedAndStore(documentVersionEntity);

        // 更新文档版本
        documentEntity.setCurrentVersionId(versionId);
        knowledgeDocumentService.updateById(documentEntity);
    }

    /**
     * 向量化存储
     * @param documentVersion
     * @return
     */
    @Override
    public Boolean embedAndStore(KnowledgeDocumentVersionEntity documentVersion) {
        if (documentVersion == null) {
            return false;
        }
        if (documentVersion.getStatus() == DocumentStatus.VECTOR_STORED) {
            log.info("文档版本状态已为VECTOR_STORED,无需重复向量化:{}", documentVersion.getVersionId());
            return true;
        }
        if (documentVersion.getStatus() != DocumentStatus.CHUNKED) {
            log.warn("文档版本状态不是CHUNKED,无法完成向量化:{}", documentVersion.getStatus());
            return false;
        }

        //指定该文档版本生效
        knowledgeDocumentService.activateVersion(documentVersion.getVersionId());

        //二次校验
        long segmentCount = knowledgeSegmentService.count(new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                .eq(KnowledgeSegmentEntity::getDocumentId, documentVersion.getDocId())
                .eq(KnowledgeSegmentEntity::getDocumentVersion, documentVersion.getVersionId())
                .eq(KnowledgeSegmentEntity::getStatus, SegmentStatus.STORED)
                .eq(KnowledgeSegmentEntity::getSkipEmbedding, 0));
        if (segmentCount == 0) {
            // 针对非当前版本的文档 ---> 取消激活
            List<KnowledgeDocumentVersionEntity> documentVersions = knowledgeDocumentVersionService.list(new LambdaQueryWrapper<KnowledgeDocumentVersionEntity>()
                    .eq(KnowledgeDocumentVersionEntity::getDocId, documentVersion.getDocId())
                    .eq(KnowledgeDocumentVersionEntity::getStatus, DocumentStatus.VECTOR_STORED)
                    .ne(KnowledgeDocumentVersionEntity::getVersionId, documentVersion.getVersionId()));
            documentVersions.forEach(version -> knowledgeDocumentService.deactivateVersion(version.getVersionId()));
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 填充元数据
     *
     * @param document         文档信息
     * @param metadata         元数据
     * @return
     */
    private static String enrichMetadata(KnowledgeDocumentEntity document, KnowledgeDocumentVersionEntity versionRecord, Metadata metadata) {
        metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
        metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
        metadata.put(MetadataKeyConstant.URL, versionRecord.getDocUrl());
        if (document.getCurrentVersionId() != null) {
            metadata.put(MetadataKeyConstant.VERSION, document.getCurrentVersionId());
        }
        Map<String, Object> metadataMap = metadata.toMap();
        metadataMap.put(MetadataKeyConstant.ACCESSIBLE_BY, document.getAccessibleBy());
        return JSON.toJSONString(metadataMap);
    }

    /**
     * 从MinIO URL 中提取对象名称
     * 例: http://8.136.10.85:9000/know-engine/converted/pdf/cffab6fa-b1e2-4c0f-a16f-6cae3f48a7cb/full.md
     * --> /converted/pdf/cffab6fa-b1e2-4c0f-a16f-6cae3f48a7cb/full.md
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int lastSlashIndex = url.lastIndexOf(bucketName) + bucketName.length();
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            return null;
        }
        return url.substring(lastSlashIndex + 1);
    }

    /**
     * 计算文件内容的SHA-256哈希值
     *
     * @param file 上传的文件
     * @return SHA-256哈希的十六进制字符串
     */
    private String calculateContentHash(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256算法不可用", e);
        }
    }
}
