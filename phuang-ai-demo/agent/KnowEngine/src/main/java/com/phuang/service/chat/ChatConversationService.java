package com.phuang.service.chat;

import com.baomidou.mybatisplus.extension.service.IService;
import com.phuang.model.entity.ChatConversationEntity;

/**
 *
 * @description ChatConversationService
 * @author huangpeng
 * @since 2026/9/6
 */

public interface ChatConversationService extends IService<ChatConversationEntity> {

    String createConversation(String userId, String title);

    boolean updateTitle(String conversationId, String title);

}
