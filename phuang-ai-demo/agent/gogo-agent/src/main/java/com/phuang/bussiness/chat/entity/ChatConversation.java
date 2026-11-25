package com.phuang.bussiness.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.phuang.handler.InstantTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 对话会话实体，对应 chat_conversation 表
 *
 * @author huangpeng
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@TableName(value = "chat_conversation", autoResultMap = true)
public class ChatConversation {

    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private String userId;

    private String title;

    @TableField(fill = FieldFill.INSERT, typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE, typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    public ChatConversation(String conversationId, String userId, String title) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title;
    }

}
