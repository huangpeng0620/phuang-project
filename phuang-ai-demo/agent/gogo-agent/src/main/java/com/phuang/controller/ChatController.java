package com.phuang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.phuang.model.request.ChatRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 *
 * @description ChatController
 * @author huangpeng
 * @since 2026/11/24
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @PostMapping("/{sessionId}")
    @SaCheckLogin
    public SseEmitter chat(@PathVariable String sessionId, @RequestBody ChatRequest request) {
        String userId = StpUtil.getLoginIdAsString();

        return null;
    }
}
