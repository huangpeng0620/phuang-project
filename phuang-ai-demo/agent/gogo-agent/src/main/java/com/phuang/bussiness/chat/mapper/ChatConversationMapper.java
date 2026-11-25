package com.phuang.bussiness.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.bussiness.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 *
 * @author huangpeng
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

}
