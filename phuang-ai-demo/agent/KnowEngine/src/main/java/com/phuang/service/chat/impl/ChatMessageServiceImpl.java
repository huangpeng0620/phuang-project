package com.phuang.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.ChatMessageMapper;
import com.phuang.model.entity.ChatMessageEntity;
import com.phuang.model.enums.ChatMessageType;
import com.phuang.service.chat.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    /**
     * 获取会话最新消息
     *
     * @param conversationId
     * @param maxMessages
     * @return
     */
    @Override
    public List<ChatMessageEntity> getRecentMessages(String conversationId, int maxMessages) {
        // 查询最新的 limit+2 条，排除最新的2条（当前轮次刚保存的user消息和空assistant消息）
        Page<ChatMessageEntity> page = this.page(
                new Page<>(1, maxMessages + 2),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByDesc(ChatMessageEntity::getCreatedAt));
        List<ChatMessageEntity> records = page.getRecords();
        // 去掉最新的2条
        if (records.size() > 2) {
            records = records.subList(2, records.size());
        } else {
            return new java.util.ArrayList<>();
        }
        // 返回列表需要反转，使其按时间正序排列
        java.util.Collections.reverse(records);
        return records;
    }

    /**
     * 删除会话所有消息
     * @param conversationId
     */
    @Override
    public void deleteMessagesByConversationId(String conversationId) {
        this.remove(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId));
    }

    /**
     * 更新问题的改写结果
     * @param chatMessageId 消息ID
     * @param transformContent 改写后的内容
     */
    @Override
    public void updateTransformContent(String chatMessageId, String transformContent) {
        ChatMessageEntity update = new ChatMessageEntity();
        update.setTransformContent(transformContent);
        this.update(update, new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getMessageId, chatMessageId));
    }
}
