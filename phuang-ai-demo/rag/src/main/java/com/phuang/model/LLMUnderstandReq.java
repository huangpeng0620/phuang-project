package com.phuang.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @description LLMUnderstandReq
 * @author huangpeng
 * @since 2026/8/3
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class LLMUnderstandReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户问题的请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 用户问题(规范化后的)
     */
    private String currentQuery;

    /**
     * 受保护的关键词,模型不能删除或修改的内容
     */
    private List<String> protectedTerms;

    /**
     * 对话历史(用于用户问题上下文补全)
     */
    private List<ConversationHistory> conversationHistoryList;

    /**
     * 可查询的数据源
     */
    private List<AvailableResource> availableResourceList;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConversationHistory implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 对话角色:user、assistant
         */
        private String role;
        /**
         * 对话内容
         */
        private String content;

    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AvailableResource implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 数据源唯一编号
         */
        private String resourceId;

        /**
         * 数据源类型: RELATIONAL_DATABASE、RAG_KNOWLEDGE_BASE
         */
        private String resourceType;

        /**
         * 可用资源名称
         */
        private String name;

        /**
         * 资源描述: 数据源能够回答什么问题
         */
        private String description;
    }
}
