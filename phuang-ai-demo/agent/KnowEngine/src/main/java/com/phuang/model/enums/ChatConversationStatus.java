package com.phuang.model.enums;

public enum ChatConversationStatus {

    /**
     * 活跃状态，正常使用中
     */
    ACTIVE,

    /**
     * 已归档，不再活跃但保留
     */
    ARCHIVED,

    /**
     * 已删除，逻辑删除标记
     */
    DELETED;
}
