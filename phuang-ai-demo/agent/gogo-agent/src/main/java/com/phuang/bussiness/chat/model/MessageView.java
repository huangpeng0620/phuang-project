package com.phuang.bussiness.chat.model;

import com.phuang.bussiness.chat.entity.ChatMessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 会话消息视图
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageView {

    private String id;

    private ChatMessageRole role;

    private String content;

    private String agentName;

    private long timestamp;

    private Map<String, Object> extra;

    private String feedback;

    private long feedbackAt;
}
