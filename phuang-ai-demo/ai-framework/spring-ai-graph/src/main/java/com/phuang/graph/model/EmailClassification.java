package com.phuang.graph.model;

/**
 * 邮件分类节点的结构化输出。
 *
 * <p>intent 建议使用 question、bug、billing、feature、complex、other；
 * urgency 建议使用 low、medium、high、critical。收到模型返回后，工作流仍会
 * 对这两个字段做归一化，避免模型大小写或意外值影响条件路由。</p>
 */
public record EmailClassification(
        String intent,
        String urgency,
        String topic,
        String summary
) implements java.io.Serializable {
}
