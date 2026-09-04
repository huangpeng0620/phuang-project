package com.phuang.service.document;

import com.phuang.model.constant.ContentType;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.FileProcessService;
import com.phuang.service.KnowledgeDocumentService;
import com.phuang.service.KnowledgeDocumentVersionService;
import com.phuang.service.impl.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * Word、PowerPoint、HTML 等通用文档的 Tika 转换处理器
 */
@Slf4j
@Service
public class TikaProcessServiceImpl implements FileProcessService {

    private static final String CONVERTED_FILE_DIR = "converted/tika/";

    private static final Set<FileType> SUPPORTED_TYPES = EnumSet.of(
            FileType.DOC,
            FileType.PPT,
            FileType.HTML,
            FileType.RTF,
            FileType.ODT,
            FileType.EPUB
    );

    @Resource
    private TikaDocumentParser tikaDocumentParser;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private KnowledgeDocumentVersionService knowledgeDocumentVersionService;

    @Override
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) {
        log.info("开始使用 Tika 解析文档, documentId={}, versionId={}",
                document.getDocId(), document.getCurrentVersionId());
        knowledgeDocumentService.advanceDocumentAndVersionStatus(
                document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTING);

        try (inputStream) {
            TikaDocumentParser.ParsedDocument parsedDocument =
                    tikaDocumentParser.parse(inputStream, extractResourceName(fileMinioUrl));
            if (parsedDocument.text().isBlank()) {
                throw new BusinessException("Tika 未从文档中提取到可用文本");
            }

            String convertedObjectName = CONVERTED_FILE_DIR
                    + document.getDocId() + "/" + document.getCurrentVersionId() + ".txt";
            String convertedUrl = fileStorageService.uploadFile(
                    convertedObjectName,
                    parsedDocument.text().getBytes(StandardCharsets.UTF_8),
                    ContentType.TEXT_PLAIN);

            knowledgeDocumentService.advanceDocumentAndVersionStatus(
                    document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTED);
            log.info("Tika 文档解析完成, documentId={}, versionId={}, mediaType={}, convertedUrl={}",
                    document.getDocId(), document.getCurrentVersionId(), parsedDocument.mediaType(), convertedUrl);
            return convertedUrl;
        } catch (Exception e) {
            resetStatus(document);
            log.error("Tika 文档解析失败, documentId={}, versionId={}",
                    document.getDocId(), document.getCurrentVersionId(), e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("Tika 文档解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return knowledgeBaseType == KnowledgeBaseType.DOCUMENT_SEARCH
                && fileType != null
                && SUPPORTED_TYPES.contains(fileType);
    }

    private void resetStatus(KnowledgeDocumentEntity document) {
        try {
            document.setStatus(DocumentStatus.UPLOADED);
            knowledgeDocumentService.updateById(document);
            var version = knowledgeDocumentVersionService.getById(document.getCurrentVersionId());
            if (version != null) {
                version.setStatus(DocumentStatus.UPLOADED);
                knowledgeDocumentVersionService.updateById(version);
            }
        } catch (Exception statusException) {
            log.error("Tika 解析失败后恢复文档状态失败, documentId={}, versionId={}",
                    document.getDocId(), document.getCurrentVersionId(), statusException);
        }
    }

    private String extractResourceName(String fileMinioUrl) {
        if (fileMinioUrl == null || fileMinioUrl.isBlank()) {
            return null;
        }
        int queryIndex = fileMinioUrl.indexOf('?');
        String path = queryIndex >= 0 ? fileMinioUrl.substring(0, queryIndex) : fileMinioUrl;
        int lastSlashIndex = path.lastIndexOf('/');
        return lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
    }
}
