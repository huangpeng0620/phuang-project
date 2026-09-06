package com.phuang.service.chat.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.ChatMessageMapper;
import com.phuang.model.entity.ChatMessageEntity;
import com.phuang.service.chat.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 *
 * @description ChatMessageServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity> implements ChatMessageService {
}
