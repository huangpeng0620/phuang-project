package com.phuang.controller;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.phuang.cleaner.DocumentCleaner;
import com.phuang.embedding.EmbeddingService;
import com.phuang.reader.DocumentReaderFactory;
import com.phuang.splitter.OverlapParagraphTextSplitter;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 *
 * @description 文件预处理统一接口
 * @author huangpeng
 * @since 2026/7/19
 */
@RestController
@RequestMapping("/rag")
public class RagReaderController {

    @Resource
    private DocumentReaderFactory documentReaderFactory;

    @Resource
    private EmbeddingService embeddingService;

    @RequestMapping("/read")
    public Boolean read(@RequestParam("filePath") String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件:" + filePath);
        }
        List<Document> documents;
        try {
            //1.文档加载
            documents = documentReaderFactory.read(file);

            //2.文档清洗
            documents = DocumentCleaner.cleanDocuments(documents);

            //3.文档分片
            documents = split(documents);

            //4.元数据填充
            addMMetadata(documents, filePath);

            //5.文档向量化存储
            embeddingService.embedAndStore(documents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Boolean.TRUE;
    }

    /**
     * 元数据填充
     * @param documents
     */
    private void addMMetadata(List<Document> documents, String filePath) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        for (Document document : documents) {
            document.getMetadata().put("filePath", filePath);
            //生效时间
            document.getMetadata().put("effective_date", LocalDateTime.now().toString());
        }
    }

    /**
     * 文档分片(固定长度)
     */
    private List<Document> split(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100, new String[]{String.valueOf('。')});
       //OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(1000, 0);
        return splitter.apply(documents);
    }
}
