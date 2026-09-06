package com.phuang.service.chat.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.ChatConversationMapper;
import com.phuang.model.entity.ChatConversationEntity;
import com.phuang.service.chat.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 *
 * @description ChatConversationServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Slf4j
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversationEntity> implements ChatConversationService {

}
