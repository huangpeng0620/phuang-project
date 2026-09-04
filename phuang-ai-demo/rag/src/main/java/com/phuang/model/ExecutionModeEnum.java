package com.phuang.model;

import lombok.AllArgsConstructor;

/**
 *
 * @description ExecutionModeEnum
 * @author huangpeng
 * @since 2026/8/3
 */
@AllArgsConstructor
public enum ExecutionModeEnum {
    DIRECT_LLM("DIRECT_LLM", "不需要查询数据源,直接交给模型回答"),
    RAG("RAG", "查询非结构化RAG知识库"),
    STRUCTURED_QUERY("RAG", "查询关系型数据库或业务API"),
    COMPOSITE("RAG", "同时查询结构化数据库和RAG知识库"),
    CLARIFY("RAG", "必须先向用户澄清");

    private String type;

    private String description;

    public String getType() {
        return this.type;
    }

    public String getDescription() {
        return this.description;
    }
}
