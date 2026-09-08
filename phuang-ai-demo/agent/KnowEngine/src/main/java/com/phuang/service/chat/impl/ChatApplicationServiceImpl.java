package com.phuang.service.chat.impl;

import cn.hutool.core.util.StrUtil;
import com.phuang.handler.memory.DatabaseChatMemoryStore;
import com.phuang.model.dto.IntentRecognitionResult;
import com.phuang.model.enums.ChatSource;
import com.phuang.service.ai.CommonChatService;
import com.phuang.service.ai.IntentRecognitionService;
import com.phuang.service.ai.TitleSummaryService;
import com.phuang.service.chat.ChatApplicationService;
import com.phuang.service.chat.ChatConversationService;
import com.phuang.service.chat.ChatMessageService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 *
 * @description ChatApplicationServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Service
@Slf4j
public class ChatApplicationServiceImpl implements ChatApplicationService {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private DatabaseChatMemoryStore databaseChatMemoryStore;

    @Resource
    private CommonChatService commonChatService;

    private IntentRecognitionService intentRecognitionService;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    /**
     * RAG 对话标题生成专用 model,采用轻量级模型,速度较快
     */
    private OpenAiChatModel titleChatModel;

    /**
     * RAG 对话生成专用 ChatModel，使用更强的模型和较低温度以提升回答质量
     */
    private StreamingChatModel ragChatModel;

    @PostConstruct
    public void init() {
        ragChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(chatModelApiKey)
                .baseUrl(chatModelBaseUrl)
                .modelName("qwen3.6-plus")
                .logRequests(true)
                .logRequests(true)
                .temperature(0.2)
                .topP(0.9)
                .customParameters(Map.of("enable_thinking", false))
                .build();
        titleChatModel = OpenAiChatModel.builder()
                .apiKey(chatModelApiKey)
                .modelName("qwen3.5-flash")
                .temperature(0.7)
                .baseUrl(chatModelBaseUrl)
                .customParameters(Map.of("enable_thinking", false))
                .build();
        intentRecognitionService = AiServices.builder(IntentRecognitionService.class).chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .chatMemoryStore(databaseChatMemoryStore).build())
                .build();
        log.info("成功初始化 ragChatModel、titleChatModel");
    }

    /**
     * 流式会话接口
     * @return
     */
    @Override
    public Flux<String> chat(String userId, String content, String conversationId, ChatSource chatSource) {
        final String finalConversationId;
        if (StrUtil.isEmpty(conversationId)) {
            //临时会话标题
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            //创建新用户会话
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建新会话: conversationId:{}, tempTitle:{}", finalConversationId, tempTitle);

            /**
             * 异步采用虚拟线程调用 LLM 生成摘要标题,完成后回写到数据库
             * <P>
             *     目前只在会话首次进行标题生成,后期可以进一步优化为【可根据后续用户问题进行持续更新】
             * </P>
             */
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    TitleSummaryService titleSummaryService = AiServices.builder(TitleSummaryService.class)
                            .chatModel(titleChatModel)
                            .build();
                    String aiTitle = titleSummaryService.generateTitle(content);
                    chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成: conversationId:{}, title:{}", finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.error("异步标题生成失败, 保留临时标题: conversationId:{}", finalConversationId, e);
                }
            });
        } else {
            finalConversationId = conversationId;
        }
        //保存消息记录
        String messageId = chatMessageService.saveUserMessage(conversationId, content);
        String aiMessageId = chatMessageService.saveAssistantMessage(conversationId);

        IntentRecognitionResult recognitionResult = intentRecognitionService.chat(conversationId, content);
        if (!recognitionResult.related()) {
            //使用通用大模型进行对话
            return commonChatService.streamChat(userId, content)
                    .concatWith(Flux.just("[DONE]:" + finalConversationId));
        } else {
            //rag流程

        }
        return null;
    }
}
