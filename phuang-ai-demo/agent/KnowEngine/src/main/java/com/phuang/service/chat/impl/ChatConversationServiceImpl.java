package com.phuang.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.ChatConversationMapper;
import com.phuang.model.entity.ChatConversationEntity;
import com.phuang.model.enums.ChatConversationStatus;
import com.phuang.service.chat.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @description ChatConversationServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Slf4j
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversationEntity> implements ChatConversationService {

    /**
     * 创建新会话
     * @param userId 用户ID
     * @param title 对话标题
     * @return
     */
    @Override
    public String createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString().replace("-", "");
        ChatConversationEntity chatConversationEntity = ChatConversationEntity.builder()
                .conversationId(conversationId)
                .title(title != null ? title : "新对话")
                .userId(userId)
                .status(ChatConversationStatus.ACTIVE)
                .build();
        this.save(chatConversationEntity);
        return conversationId;
    }

    /**
     * 更新会话标题
     * @param conversationId 会话ID
     * @param title 会话标题
     * @return
     */
    @Override
    public boolean updateTitle(String conversationId, String title) {
        return this.update(new LambdaUpdateWrapper<ChatConversationEntity>()
                .eq(ChatConversationEntity::getConversationId, conversationId)
                .set(ChatConversationEntity::getTitle, title)
                .set(ChatConversationEntity::getUpdatedAt, LocalDateTime.now()));
    }
}
