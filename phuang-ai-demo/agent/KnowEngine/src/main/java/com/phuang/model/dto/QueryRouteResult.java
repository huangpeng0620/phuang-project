package com.phuang.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询路由结果
 *
 * @author phuang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRouteResult {

    /** 用户问题的核心意图 */
    @JsonPropertyDescription("用户问题的核心意图，仅使用以下三个字符串值：relational_db、graph_db、knowledge_base")
    private String intent;

    /** 推荐的查询策略 */
    @JsonPropertyDescription("推荐的查询策略")
    private String strategy;

    /** 策略推荐的置信度 */
    @JsonPropertyDescription("策略推荐的置信度（0–1），评分保留两位小数")
    private double confidence;

    /** 路由判断依据 */
    @JsonPropertyDescription("推理理由")
    private String reasoning;
}
