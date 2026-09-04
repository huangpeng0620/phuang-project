package com.phuang.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 *
 * @description LLM查询理解返回的对象示例
 * @author huangpeng
 * @since 2026/8/3
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class LLMQueryPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 结合历史对话补全后的完整问题
     */
    private String standaloneQuery;

    /**
     * 意图类型 llm_query_understand_prompt
     */
    private String intentType;

    /**
     * 后面查询的处理链路
     */
    private String executionMode;

    /**
     * 表示是否必须先向用户澄清问题
     */
    private Clarification clarification;

    /**
     * 表示当前问题是否被拆成多个独立查询任务
     * true  → queryTasks包含多个子任务
     * false → queryTasks通常只有一个主任务
     */
    private Boolean isDecomposed;

    /**
     * 任务列表
     */
    private List<QueryTask> queryTaskList;

    /**
     * 表示模型为什么选择当前执行方式
     */
    private String reason;

    /**
     * 表示模型对整体判断的置信度，范围是：0 ~ 1
     */
    private BigDecimal confidence;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryTask implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 子任务唯一编号
         */
        private String taskId;

        /**
         * 表示可以独立执行的完整查询
         */
        private String query;

        /**
         * 简要说明为什么生成这个任务
         */
        private String purpose;

        /**
         * 当前任务需要访问哪些数据源
         */
        private List<String> resourceIds;

        /**
         * 支持哪些意图
         */
        private List<String> supportedIntents;

        /**
         * 不能做什么
         */
        private List<String> notSuitableFor;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Clarification implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前问题是否存在阻塞性歧义
         */
        private Boolean required;

        /**
         * 返回给用户的追问
         */
        private String question;

        /**
         * 表示可以给用户展示的候选答案
         */
        private List<String> options;
    }

}
