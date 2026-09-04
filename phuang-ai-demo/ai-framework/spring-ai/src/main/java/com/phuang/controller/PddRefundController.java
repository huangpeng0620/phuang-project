package com.phuang.controller;

import com.phuang.model.ChatStatus;
import com.phuang.model.OrderChat;
import com.phuang.tools.OrderTools;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 *
 * @description PddRefundController
 * @author huangpeng
 * @since 2026/4/8
 */
@RestController
@RequestMapping("/pdd/refund")
public class PddRefundController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    private ChatClient chatClient;

    @Value("classpath:templates/pdd_refund_system_prompt.pt")
    private org.springframework.core.io.Resource systemText;

    @Resource
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Resource
    private OrderTools orderTools;

    /**
     * 用户首次对话接口(用于初始化用户对话历史)
     * @param userId 用户ID
     * @param orderId 订单ID
     * @return
     */
    @GetMapping("/newChat")
    public OrderChat newChat(@RequestParam("userId") String userId,
                             @RequestParam("orderId") String orderId) {
        String chatId = UUID.randomUUID().toString();
        return chatClient
                .prompt()
                .user(String.format("我要咨询订单相关的售后问题，我的用户id是%s,我的订单号是:%s,本地的对话Id是%s,当前状态是%s", userId, orderId, chatId, ChatStatus.CHAT_START.name()))
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .call()
                .entity(OrderChat.class);
    }

    /**
     * 用户沟通接口
     * @param question 问题内容
     * @param chatId   对话ID
     * @return
     */
    @GetMapping("/ask")
    public Flux<String> ask(@RequestParam("question") String question,
                            @RequestParam("chatId") String chatId) {
        return chatClient
                .prompt()
                .user(question)
                .tools(orderTools)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * 初始化 chat client
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .build();
        this.chatClient = ChatClient.builder(deepSeekR1ChatModel)
                //设置提示词
                .defaultSystem(systemText)
                //持久化记忆
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
