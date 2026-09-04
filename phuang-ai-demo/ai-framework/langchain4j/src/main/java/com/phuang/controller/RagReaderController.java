package com.phuang.controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

/**
 *
 * @description RagReaderController
 * @author huangpeng
 * @since 2026/8/26
 */
@RestController
@RequestMapping("/rag")
public class RagReaderController {

    @Resource
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @Resource
    private PgVectorEmbeddingStore pgVectorEmbeddingStore;

    @RequestMapping("/read")
    public Boolean read(@RequestParam("filePath") String filePath) {
        //文档加载
        Document document = loadDocument(filePath, new ApacheTikaDocumentParser());
        //文档解析
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(100, 0);
        List<TextSegment> textSegments = splitter.split(document);
        //向量化处理
        List<Embedding> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < textSegments.size(); i = i + 9) {
            List<TextSegment> segmentList = textSegments.subList(i, Math.min(i + 9, textSegments.size()));
            List<Embedding> embeddings = openAiEmbeddingModel.embedAll(segmentList).content();
            allEmbeddings.addAll(embeddings);
        }

        //EmbeddingStoreIngestor 简化 todo

        //向量存储
        pgVectorEmbeddingStore.addAll(allEmbeddings, textSegments);
        return Boolean.TRUE;
    }
}
