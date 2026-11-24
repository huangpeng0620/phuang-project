package com.phuang.model.request;

import lombok.Data;

/**
 * Chat 请求体。userId 已由 Sa-Token 从会话读取，无需前端传入
 *
 * @author huangpeng
 */
@Data
public class ChatRequest {

    private String message;

}
