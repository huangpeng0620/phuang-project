package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话消息表 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

}
