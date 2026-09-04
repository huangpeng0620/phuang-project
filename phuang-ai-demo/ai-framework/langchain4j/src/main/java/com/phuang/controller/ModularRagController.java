package com.phuang.controller;

import com.phuang.service.LangChainAiService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 *
 * @description ModularRagController
 * @author huangpeng
 * @since 2026/8/25
 */
@RestController
@RequestMapping("/modular/rag")
public class ModularRagController {

    @Resource
    private OpenAiChatModel chatModel;

    /*@GetMapping("/retrieve")
    public String retrieve(@RequestParam("msg") String msg, @RequestParam("conversationId") String conversationId) {

        *//**
         * 1.构建文档检索增强器
         *//*
        //1.2 向量模型
        OpenAiEmbeddingModel openAiEmbeddingModel = OpenAiEmbeddingModel.builder()
                .modelName("text-embedding-v4")
                .dimensions(768)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("sk-d1e56d0012004a2cbdf6b3ddb2a8cc3d")
                .build();
        //1.2 向量存储
        PgVectorEmbeddingStore pgVectorEmbeddingStore = PgVectorEmbeddingStore.builder()
                .host("8.136.10.85")
                .port(5432)
                .user("pgvector")
                .password("pgvector")
                .database("rag_test")
                .table("vector_test")
                .dimension(openAiEmbeddingModel.dimension())
                .build();
        //1.3 构建向量检索
        EmbeddingStoreContentRetriever embeddingStoreContentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(pgVectorEmbeddingStore)
                .embeddingModel(openAiEmbeddingModel)
                .maxResults(5)
                .minScore(0.7)
                .build();

        *//**
         * 2.查询改写
         *//*
        //2.1查询改写 - 富化 todo 需要测试下是否生效
        CompressingQueryTransformer compressingQueryTransformer = CompressingQueryTransformer.builder()
                .chatModel(chatModel)
                .build();
        //2.2查询改写 - 查询拓展
        ExpandingQueryTransformer expandingQueryTransformer = ExpandingQueryTransformer.builder()
                .chatModel(chatModel)
                .n(3)
                .build();
        //2.3 定义执行顺序：原始问题 -> 压缩为独立问题 -> 扩展为 3 个检索问题
        QueryTransformer compositeQueryTransformer = query ->
                compressingQueryTransformer.transform(query).stream()
                        .flatMap(compressedQuery -> expandingQueryTransformer.transform(compressedQuery).stream()).toList();

        //自定义 QueryRouter实现混合检索 todo

        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(embeddingStoreContentRetriever)
                .queryTransformer(compositeQueryTransformer)
                .build();

        //构建最终的AI服务
        LangChainAiService langChainAiService = AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        //调用AI服务
        return langChainAiService.chat(msg);
    }*/


}
