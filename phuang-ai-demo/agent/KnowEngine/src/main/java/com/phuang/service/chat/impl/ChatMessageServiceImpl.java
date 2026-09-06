package com.phuang.service.chat.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.ChatMessageMapper;
import com.phuang.model.entity.ChatMessageEntity;
import com.phuang.model.enums.ChatMessageType;
import com.phuang.service.chat.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 *
 * @description ChatMessageServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity> implements ChatMessageService {

    /**
     * 保存用户消息
     * @param conversationId 会话ID
     * @param content 会话内容
     * @return
     */
    @Override
    public String saveUserMessage(String conversationId, String content) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        ChatMessageEntity chatMessageEntity = ChatMessageEntity.builder()
                .messageId(messageId)
                .type(ChatMessageType.USER)
                .conversationId(conversationId)
                .content(content)
                .build();
        this.save(chatMessageEntity);
        return messageId;
    }

    /**
     * 创建AI消息记录
     * @param conversationId 会话ID
     * @return
     */
    @Override
    public String saveAssistantMessage(String conversationId) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        ChatMessageEntity chatMessageEntity = ChatMessageEntity.builder()
                .messageId(messageId)
                .type(ChatMessageType.USER)
                .conversationId(conversationId)
                .build();
        this.save(chatMessageEntity);
        return messageId;
    }
}
