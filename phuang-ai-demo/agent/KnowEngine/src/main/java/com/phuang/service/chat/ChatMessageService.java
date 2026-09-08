package com.phuang.service.chat;

import com.baomidou.mybatisplus.extension.service.IService;
import com.phuang.model.entity.ChatMessageEntity;

import java.util.List;

/**
 *
 * @description ChatMessageService
 * @author huangpeng
 * @since 2026/9/6
 */

public interface ChatMessageService extends IService<ChatMessageEntity> {

    String saveUserMessage(String conversationId, String content);

    String saveAssistantMessage(String conversationId);

    List<ChatMessageEntity> getRecentMessages(String conversationId, int maxMessages);

    void deleteMessagesByConversationId(String conversationId);
}
