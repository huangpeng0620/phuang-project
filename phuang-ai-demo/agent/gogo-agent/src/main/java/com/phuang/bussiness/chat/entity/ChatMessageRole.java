package com.phuang.bussiness.chat.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/** 聊天消息的发送方角色。 */
public enum ChatMessageRole {
    USER("user"),
    AGENT("agent"),
    SYSTEM("system");

    @EnumValue
    @JsonValue
    private final String value;

    ChatMessageRole(String value) {
        this.value = value;
    }
}
