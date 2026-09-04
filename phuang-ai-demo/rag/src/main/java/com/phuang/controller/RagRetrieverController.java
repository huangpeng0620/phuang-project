package com.phuang.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description rag 检索控制器
 * @author huangpeng
 * @since 2026/7/26
 */
@RestController
@RequestMapping("/rag")
public class RagRetrieverController implements InitializingBean {

    @Resource
    private ChatModel chatModel;

    @Resource
    private VectorStore vectorStore;

    private ChatClient chatClient;

    @GetMapping("/retrieve")
    public String retrieve(@RequestParam("query") String query) {
        return chatClient
                .prompt(query)
                .advisors(spec -> spec.param(
                        QuestionAnswerAdvisor.FILTER_EXPRESSION, "tenant == 'company-a' && type == 'employee-policy'"))
                .advisors(questionAnswerAdvisor -> questionAnswerAdvisor.param("qa_filter_expression", "filePath == '" + "/Users/phuang/Downloads/RAG材料/Java八股文介绍.docx" + "'"))
                .call()
                .content();
    }

    @Override
    public void afterPropertiesSet() {
        PromptTemplate promptTemplate = new PromptTemplate("""
                请基于以下提供的参考文档内容，回答用户的问题。
                如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                参考文档内容:
                {question_answer_context}
                用户问题: {query}
                """);
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.5) // 只保留相似度达到阈值的文档
                        .topK(5) //最多取回五个文档
                        .build())
                .promptTemplate(promptTemplate)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(questionAnswerAdvisor)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withTopP(0.7)
                        .build()
                ).build();
    }
}
