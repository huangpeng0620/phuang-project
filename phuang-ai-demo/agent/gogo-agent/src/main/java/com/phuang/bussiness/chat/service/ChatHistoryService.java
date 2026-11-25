package com.phuang.bussiness.chat.service;

import com.phuang.bussiness.chat.model.ConversationView;
import com.phuang.bussiness.chat.model.MessageView;

import java.util.List;

/**
 *
 * @description ChatHistoryService
 * @author huangpeng
 * @since 2026/11/25
 */

public interface ChatHistoryService {

    List<ConversationView> listConversations(String userId);

    List<MessageView> listMessages(String conversationId, String userId);

    void saveUserMessage(String conversationId, String userId, String content);

    void updateTitle(String conversationId, String userId, String title);

    void deleteConversation(String conversationId, String userId);
}
