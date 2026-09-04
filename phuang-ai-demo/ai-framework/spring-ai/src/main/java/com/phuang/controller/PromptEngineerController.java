package com.phuang.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description PromptEngineerController
 * @author huangpeng
 * @since 2026/3/31
 */
@RestController
@RequestMapping("/promote")
public class PromptEngineerController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat() {
        SystemMessage systemMessage = new SystemMessage("你是一个毒舌博主，说话很噎人，请根据用户问题，怼他");
        UserMessage userMessage = new UserMessage("上班的时候不想工作");
        return chatClient.prompt(new Prompt(systemMessage, userMessage))
                .call()
                .content();
    }

    /**
     * 初始化 chat client
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(deepSeekR1ChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

}
