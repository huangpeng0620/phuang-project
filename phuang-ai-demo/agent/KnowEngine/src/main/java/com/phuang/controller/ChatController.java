package com.phuang.controller;

import com.phuang.model.enums.ChatSource;
import com.phuang.service.AuthService;
import com.phuang.service.chat.ChatApplicationService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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

    @Resource
    private AuthService authService;

    /**
     * 流式对话接口
     * @param content 用户问题
     * @param conversationId 会话ID
     * @return
     */
    @GetMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(@RequestParam("content") String content,
                             @RequestParam(value = "conversationId", required = false) String conversationId) {
        String userId = authService.getCurrentUserId();
        return chatApplicationService.chat(userId, content, conversationId, ChatSource.USER_WEB);
    }
}
