package com.phuang.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description ModularRagController
 * @author huangpeng
 * @since 2026/8/24
 */
@RestController
@RequestMapping("/modular/rag")
public class ModularRagController implements InitializingBean {

    @Resource
    private ChatModel chatModel;

    @Resource
    private VectorStore vectorStore;

    private ChatClient chatClient;

    @GetMapping("/retrieve")
    public String retrieve(@RequestParam("query") String query, @RequestParam("conversationId") String conversationId) {
        return chatClient
                .prompt(query)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //文档检索
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)            //绑定向量存储
                .topK(5)                             // 返回最相似的 5 个文档
                .similarityThreshold(0.6)            // 相似度低于 0.6 的过滤掉
                .build();
        //问题改写
        CompressionQueryTransformer compressionQueryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel).build().mutate())
                .build();
        RewriteQueryTransformer rewriteQueryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel).build().mutate())
                .build();
        TranslationQueryTransformer translationQueryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel).build().mutate())
                .targetLanguage("English")
                .build();
        //查询拓展
        MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel).build().mutate())
                .numberOfQueries(3)                //拓展的条数
                .includeOriginal(true)            //是否包含原始问题
                .build();
        //文档合并
        ConcatenationDocumentJoiner concatenationDocumentJoiner = new ConcatenationDocumentJoiner();
        //上下文增强
        ContextualQueryAugmenter contextualQueryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();
        //记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(compressionQueryTransformer, rewriteQueryTransformer, translationQueryTransformer)
                .documentRetriever(documentRetriever)
                .queryExpander(multiQueryExpander)
                .documentJoiner(concatenationDocumentJoiner)
                .queryAugmenter(contextualQueryAugmenter)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor, retrievalAugmentationAdvisor)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withTopP(0.7)
                        .build()
                ).build();
    }
}
