package com.phuang.service;


import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileProcessServiceFactory {

    @Resource
    private List<FileProcessService> fileProcessServiceList;

    public FileProcessService get(FileType fileProcessType, KnowledgeBaseType knowledgeBaseType) {
        return fileProcessServiceList.stream()
                .filter(service -> service.supports(fileProcessType, knowledgeBaseType))
                .findFirst()
                .orElse(null);
    }
}
