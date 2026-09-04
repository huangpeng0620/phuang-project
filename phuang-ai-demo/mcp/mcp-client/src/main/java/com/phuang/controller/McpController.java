package com.phuang.controller;

import com.phuang.service.RetrySSEMcpService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description McpController
 * @author huangpeng
 * @since 2026/3/31
 */
@RestController
@RequestMapping("/mcp")
public class McpController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    private ChatClient chatClient;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private RetrySSEMcpService retrySSEMcpService;

    @GetMapping("/simpleCall")
    public String simpleCall(@RequestParam("message") String message) {
        return chatClient.prompt(message)
                .call()
                .content();
    }

    @GetMapping("/retryChat")
    public String retry(@RequestParam("message") String message) {
        return retrySSEMcpService.chat(message);
    }

    /**
     * 初始化 chat client
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(deepSeekR1ChatModel)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.7)
                        .build())
                .build();
    }
}
