package com.phuang.bussiness.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.phuang.handler.InstantTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 对话消息实体，对应 chat_message 表。
 *
 * @author Hollis
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage {

    @TableId(value = "message_id", type = IdType.INPUT)
    private String messageId;

    private String conversationId;

    private String role;

    private String content;

    private String agentName;

    /**
     * 扩展信息 JSON 字符串，可存放进度快照、推荐问题等
     */
    private String extra;

    /**
     * 用户反馈：LIKE 点赞 / DISLIKE 点踩 / NULL 未反馈
     */
    private String feedback;

    /**
     * 反馈时间（毫秒为单位的 Instant）
     */
    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant feedbackAt;

    @TableField(fill = FieldFill.INSERT, typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;
}
