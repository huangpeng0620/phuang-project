package com.phuang.service.document.impl;

import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件处理服务 - 负责PDF文件转换处理
 */
@Slf4j
@Service
public class PdfProcessServiceImpl extends MinerUProcessBaseServiceImpl {

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.PDF;
    }
}

