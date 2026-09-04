package com.phuang.service.document;

import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.service.FileProcessService;
import com.phuang.service.KnowledgeDocumentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 *
 * @description ExcelProcessServiceImpl
 * @author huangpeng
 * @since 2026/9/2
 */
@Service
@Slf4j
public class ExcelProcessServiceImpl implements FileProcessService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        if (FileType.EXCEL.equals(fileType) || FileType.CSV.equals(fileType)) {
            return knowledgeBaseType == KnowledgeBaseType.DATA_QUERY;
        }
        return false;
    }

    @Override
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) throws Exception {
        knowledgeDocumentService.advanceDocumentAndVersionStatus(
                document.getDocId(), document.getCurrentVersionId(), DocumentStatus.STORED);
        return fileMinioUrl;
    }
}
