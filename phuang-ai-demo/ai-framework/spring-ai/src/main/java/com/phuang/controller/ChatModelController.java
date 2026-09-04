package com.phuang.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/model")
public class ChatModelController {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    /**
     * 简易文本对话
     * @param message
     * @return
     */
    @GetMapping("/call/string")
    public String callString(@RequestParam("message") String message) {
        return deepSeekR1ChatModel.call(message);
    }

    /**
     * 自定义 message 对话
     * @return
     */
    @GetMapping("/call/message")
    public String callMessage() {
        SystemMessage systemMessage = new SystemMessage("你是一个诗人");
        UserMessage userMessage = new UserMessage("写一首关于冬天的诗");
        return deepSeekR1ChatModel.call(systemMessage, userMessage);
    }

    /**
     * 自定义 prompt 对话
     * @return
     */
    @GetMapping("/call/prompt")
    public String callPrompt() {
        SystemMessage systemMessage = new SystemMessage("你是一个诗人");
        UserMessage userMessage = new UserMessage("写一首关于冬天的诗");
        Prompt prompt = Prompt.builder()
                .messages(systemMessage, userMessage)
                .build();
        return deepSeekR1ChatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();
    }

}
