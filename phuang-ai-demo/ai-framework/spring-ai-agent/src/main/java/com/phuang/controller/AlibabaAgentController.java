package com.phuang.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description AlibabaAgentController
 * @author huangpeng
 * @since 2026/9/20
 */
@RestController
@RequestMapping("/alibaba/react")
public class AlibabaAgentController {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        ReactAgent agent = ReactAgent.builder()
                .name("chat_agent")
                .model(deepSeekR1ChatModel)
                .systemPrompt("你是一个生活问答助手")
                .build();

        return null;
    }


}
