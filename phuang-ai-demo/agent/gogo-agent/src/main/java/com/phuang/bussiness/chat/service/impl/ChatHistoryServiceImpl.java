package com.phuang.bussiness.chat.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.phuang.bussiness.chat.entity.ChatConversation;
import com.phuang.bussiness.chat.entity.ChatMessage;
import com.phuang.bussiness.chat.entity.ChatMessageRole;
import com.phuang.bussiness.chat.mapper.ChatConversationMapper;
import com.phuang.bussiness.chat.mapper.ChatMessageMapper;
import com.phuang.bussiness.chat.model.ConversationView;
import com.phuang.bussiness.chat.model.MessageView;
import com.phuang.bussiness.chat.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @description ChatHistoryService
 * @author huangpeng
 * @since 2026/11/25
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final String DEFAULT_TITLE = "新对话";

    /** 对话标题最大字符数 */
    private static final int TITLE_MAX_LENGTH = 24;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatConversationMapper chatConversationMapper;

    /**
     * 查询某用户的所有会话，按最后更新时间倒序
     * @param userId 用户ID
     * @return 会话视图列表
     */
    @Override
    public List<ConversationView> listConversations(String userId) {
        LambdaQueryWrapper<ChatConversation> wrapper = new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getUpdatedAt);
        return chatConversationMapper.selectList(wrapper).stream()
                .map(this::toConversationView)
                .collect(Collectors.toList());
    }

    /**
     * 查询某会话下的所有消息（需校验会话属于当前用户）
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return
     */
    public List<MessageView> listMessages(String conversationId, String userId) {
        ChatConversation conversation = Optional.ofNullable(chatConversationMapper.selectById(conversationId))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getCreatedAt)).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
    }

    /**
     * 查询某会话下最近 N 条消息（按时间正序返回）
     *
     * <p>
     *     用于问题改写智能体加载跨 Agent 的全量对话上下文
     * </p>
     *
     * @param conversationId 会话 ID
     * @param limit          最大条数
     * @return 按时间正序排列的消息实体列表
     */
    @Override
    public List<ChatMessage> findRecentMessages(String conversationId, int limit) {
        try {
            List<ChatMessage> desc = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .orderByDesc(ChatMessage::getCreatedAt)
                    .last("LIMIT " + limit));
            // 反转为时间正序，方便调用方直接拼接上下文
            Collections.reverse(desc);
            return desc;
        } catch (Exception e) {
            log.warn("[CHAT_HISTORY] 查询最近消息失败 conversationId={}: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 保存一条用户消息,同时创建/更新会话标题
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param content 消息内容
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String conversationId, String userId, String content) {
        ensureConversationExists(conversationId, userId);
        if (content != null && !content.isBlank()) {
            updateTitleFromFirstUserMessage(conversationId, content);
        }
        ChatMessage message = new ChatMessage(generateMessageId(), conversationId, ChatMessageRole.USER, content, null, null);
        chatMessageMapper.insert(message);
        log.debug("[CHAT_HISTORY] 保存用户消息 conversationId={}", conversationId);
    }

    /**
     * 保存一条 AI 回复消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveAssistantMessage(String conversationId, String userId, String content, String agentName, String extraJson) {
        ensureConversationExists(conversationId, userId);
        String messageId = generateMessageId();
        ChatMessage message = new ChatMessage(messageId, conversationId, ChatMessageRole.AGENT, content, agentName, extraJson);
        chatMessageMapper.insert(message);
        log.debug("[CHAT_HISTORY] 保存AI消息 conversationId={}, agent={}, messageId={}", conversationId, agentName, messageId);
        return messageId;
    }

    /**
     * 判断会话是否存在,不存在则创建一条新的
     */
    private void ensureConversationExists(String conversationId, String userId) {
        if (Optional.ofNullable(chatConversationMapper.selectById(conversationId)).isEmpty()) {
            ChatConversation conversation = new ChatConversation(conversationId, userId, DEFAULT_TITLE);
            chatConversationMapper.insert(conversation);
        }
    }

    private void updateTitleFromFirstUserMessage(String conversationId, String content) {
        ChatConversation conversation = Optional.ofNullable(chatConversationMapper.selectById(conversationId)).orElse(null);
        if (conversation == null) {
            return;
        }
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = content.length() > TITLE_MAX_LENGTH ? content.substring(0, TITLE_MAX_LENGTH) : content;
            chatConversationMapper.update(new LambdaUpdateWrapper<ChatConversation>()
                    .eq(ChatConversation::getConversationId, conversationId)
                    .set(ChatConversation::getTitle, title));
        }
    }

    /**
     * 生成消息 ID
     */
    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 更新会话标题
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param title 标题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(String conversationId, String userId, String title) {
        ChatConversation conversation = Optional.ofNullable(chatConversationMapper.selectById(conversationId))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        chatConversationMapper.update(new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .set(ChatConversation::getTitle, title));
    }

    /**
     * 保存一条系统消息（如错误提示、中断提示）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSystemMessage(String conversationId, String userId, String content) {
        ensureConversationExists(conversationId, userId);
        ChatMessage message = new ChatMessage(generateMessageId(), conversationId, ChatMessageRole.SYSTEM, content, null, null);
        chatMessageMapper.insert(message);
    }

    /**
     * 删除会话及其消息（逻辑删除）
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteConversation(String conversationId, String userId) {
        ChatConversation conversation = Optional.ofNullable(chatConversationMapper.selectById(conversationId))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        //逻辑删除会话消息记录
        chatMessageMapper.update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .set(ChatMessage::getDeleted, 1));
        // 逻辑删除会话记录
        chatConversationMapper.deleteById(conversationId);
        log.info("[CHAT_HISTORY] 删除会话 conversationId={}, userId={}", conversationId, userId);
    }

    private ConversationView toConversationView(ChatConversation conversation) {
        return ConversationView.builder()
                .id(conversation.getConversationId())
                .title(conversation.getTitle())
                .createdAt(toEpochMilli(conversation.getCreatedAt()))
                .updatedAt(toEpochMilli(conversation.getUpdatedAt()))
                .build();
    }

    private MessageView toMessageView(ChatMessage message) {
        Map<String, Object> extraMap = null;
        if (message.getExtra() != null && !message.getExtra().isBlank()) {
            try {
                extraMap = JSON.parseObject(message.getExtra(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                log.warn("[CHAT_HISTORY] 解析消息 extra 失败 messageId={}:{}", message.getMessageId(), e.getMessage());
            }
        }
        return MessageView.builder()
                .id(message.getMessageId())
                .role(message.getRole())
                .content(message.getContent())
                .agentName(message.getAgentName())
                .timestamp(toEpochMilli(message.getCreatedAt()))
                .extra(extraMap == null ? Collections.emptyMap() : extraMap)
                .feedback(message.getFeedback())
                .feedbackAt(toEpochMilli(message.getFeedbackAt()))
                .build();
    }

    private long toEpochMilli(Instant instant) {
        return instant == null ? System.currentTimeMillis() : instant.toEpochMilli();
    }

}
