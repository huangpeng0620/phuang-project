package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.ChatConversationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话会话表 Mapper
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationEntity> {

}
