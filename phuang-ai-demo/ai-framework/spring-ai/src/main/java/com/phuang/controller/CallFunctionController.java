package com.phuang.controller;

import com.phuang.tools.WeatherFunction;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description CallFunctionController
 * @author huangpeng
 * @since 2026/3/31
 */
@RestController
@RequestMapping("/tool")
public class CallFunctionController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat() {
        UserMessage userMessage = new UserMessage("今日杭州天气");
        return chatClient.prompt(new Prompt(userMessage))
                .tools(new WeatherFunction())
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
