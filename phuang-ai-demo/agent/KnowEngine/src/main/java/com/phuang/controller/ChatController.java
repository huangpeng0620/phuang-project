package com.phuang.controller;

import com.phuang.model.enums.ChatSource;
import com.phuang.service.chat.ChatApplicationService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 *
 * @description 对话接口
 * @author huangpeng
 * @since 2026/9/6
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatApplicationService chatApplicationService;

    /**
     * 流式对话接口
     * @param content 用户问题
     * @param conversationId 会话ID
     * @param userId 用户ID 后面替换token获取 todo
     * @return
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(@RequestParam("content") String content,
                             @RequestParam(value = "conversationId", required = false) String conversationId,
                             @RequestParam("userId") String userId) {
        return chatApplicationService.chat(userId, content, conversationId, ChatSource.USER_WEB);
    }
}
