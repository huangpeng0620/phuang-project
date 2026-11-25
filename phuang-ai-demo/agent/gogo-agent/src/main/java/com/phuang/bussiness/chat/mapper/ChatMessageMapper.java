package com.phuang.bussiness.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.bussiness.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 *
 * @author huangpeng
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

}
