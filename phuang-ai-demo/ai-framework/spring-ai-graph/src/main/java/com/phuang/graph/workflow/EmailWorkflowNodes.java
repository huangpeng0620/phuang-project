package com.phuang.graph.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.phuang.graph.model.EmailClassification;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.phuang.graph.workflow.EmailWorkflowState.*;

/**
 * 邮件处理工作流的节点实现。
 *
 * <p>每个方法返回一个 NodeAction。节点读取传入的共享状态，返回“本节点要更新的字段”；
 * Graph 会按字段策略把这些增量合并到工作流状态中。</p>
 */
public final class EmailWorkflowNodes {

    private static final Set<String> INTENTS = Set.of("question", "bug", "billing", "feature", "complex", "other");
    private static final Set<String> URGENCIES = Set.of("low", "medium", "high", "critical");

    private final ChatClient chatClient;

    public EmailWorkflowNodes(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 第一个节点只把输入邮件放进执行记录，便于从 Graph 历史中看清流程起点。 */
    public NodeAction readEmail() {
        return state -> Map.of(
                CURRENT_STEP, READ_EMAIL,
                STATUS, "PROCESSING",
                CUSTOMER_HISTORY, List.of("示例客户历史：当前演示未连接客户系统，暂无历史记录。"),
                MESSAGES, List.of("已接收邮件 " + stringValue(state, EMAIL_ID))
        );
    }

    /**
     * 让模型输出结构化分类，再根据意图和紧急程度决定第一条分支。
     *
     * <p>账单问题或 critical 邮件直接进入人工审核；一般问题/功能咨询先查知识库；
     * bug 先创建工单；其他类型则直接起草回复。</p>
     */
    public NodeAction classifyIntent() {
        return state -> {
            String email = stringValue(state, EMAIL_CONTENT);
            EmailClassification raw = chatClient.prompt()
                    .system("你是客服邮件分流助手。请阅读邮件并只返回结构化结果。"
                            + "intent 只能是 question、bug、billing、feature、complex、other；"
                            + "urgency 只能是 low、medium、high、critical。topic 用简短主题，summary 用中文概括诉求。")
                    .user("发件人：" + stringValue(state, SENDER_EMAIL) + "\n邮件内容：\n" + email)
                    .call()
                    .entity(EmailClassification.class);

            EmailClassification classification = normalize(raw);
            String firstNode = chooseFirstNode(classification);
            String status = HUMAN_REVIEW.equals(firstNode) ? "WAITING_FOR_HUMAN" : "PROCESSING";

            return Map.of(
                    CLASSIFICATION, classification,
                    NEXT_NODE, firstNode,
                    CURRENT_STEP, CLASSIFY_INTENT,
                    STATUS, status,
                    MESSAGES, List.of("邮件分类完成：intent=" + classification.intent()
                            + ", urgency=" + classification.urgency()
                            + "，下一步=" + firstNode)
            );
        };
    }

    /**
     * 模拟知识库检索节点。这里故意使用固定示例结果，让读者先聚焦 Graph 的分支和状态；
     * 实际项目可在此替换成向量库、搜索服务或内部知识中心。
     */
    public NodeAction searchDocumentation() {
        return state -> {
            String email = stringValue(state, EMAIL_CONTENT).toLowerCase(Locale.ROOT);
            List<String> results;
            if (email.contains("密码") || email.contains("password")) {
                results = List.of("帮助中心：登录后进入「账号设置 - 安全」，可修改登录密码。"
                        + "若无法登录，请使用登录页的「忘记密码」流程验证邮箱。");
            } else if (email.contains("账单") || email.contains("退款") || email.contains("billing")) {
                results = List.of("帮助中心：账单与退款申请需要提供订单号；重复扣费会由账务团队核对。");
            } else {
                results = List.of("帮助中心：请提供相关订单号、操作时间和页面截图，客服可据此进一步排查。");
            }

            return Map.of(
                    SEARCH_RESULTS, results,
                    NEXT_NODE, DRAFT_RESPONSE_NODE,
                    CURRENT_STEP, SEARCH_DOCUMENTATION,
                    MESSAGES, List.of("已完成示例知识库检索（当前使用 mock 数据）")
            );
        };
    }

    /** 模拟创建缺陷工单，生成一个便于在响应中追踪的演示编号。 */
    public NodeAction bugTracking() {
        return state -> {
            String ticketId = "BUG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            return Map.of(
                    TICKET_ID, ticketId,
                    SEARCH_RESULTS, List.of("已创建缺陷工单 " + ticketId + "，等待工程团队排查。"),
                    NEXT_NODE, DRAFT_RESPONSE_NODE,
                    CURRENT_STEP, BUG_TRACKING,
                    MESSAGES, List.of("已创建示例缺陷工单 " + ticketId + "（当前未连接真实工单系统）")
            );
        };
    }

    /** 使用分类结果、邮件原文和检索上下文生成回复草稿。 */
    public NodeAction draftResponse() {
        return state -> {
            EmailClassification classification = classification(state);
            List<String> supportContext = new ArrayList<>(stringListValue(state, SEARCH_RESULTS));
            supportContext.addAll(stringListValue(state, CUSTOMER_HISTORY));
            String context = String.join("\n", supportContext);
            String draft = chatClient.prompt()
                    .system("你是专业、友善的中文客服。请针对客户邮件起草简洁明确的回复。"
                            + "只使用给定背景，不要编造已退款、已修复或已完成的操作；"
                            + "如果信息不足，说明需要客户补充什么。不要输出分析过程。")
                    .user("客户邮件：\n" + stringValue(state, EMAIL_CONTENT)
                            + "\n\n邮件分类：" + classification
                            + "\n\n知识库/工单背景：\n" + (StringUtils.hasText(context) ? context : "暂无额外背景"))
                    .call()
                    .content();

            if (!StringUtils.hasText(draft)) {
                throw new IllegalStateException("模型没有生成有效的回复草稿");
            }

            // 高风险或复杂邮件必须先等待人工审核；其他邮件可进入发送节点。
            boolean needsReview = "high".equals(classification.urgency())
                    || "critical".equals(classification.urgency())
                    || "complex".equals(classification.intent());
            String nextNode = needsReview ? HUMAN_REVIEW : SEND_REPLY;

            return Map.of(
                    DRAFT_RESPONSE, draft.strip(),
                    NEXT_NODE, nextNode,
                    CURRENT_STEP, DRAFT_RESPONSE_NODE,
                    STATUS, needsReview ? "WAITING_FOR_HUMAN" : "PROCESSING",
                    MESSAGES, List.of(needsReview
                            ? "回复草稿已生成，邮件风险较高，等待人工审核"
                            : "回复草稿已生成，可以自动发送")
            );
        };
    }

    /**
     * 人工审核节点在 compile 配置的 interruptBefore 处暂停后才会执行。
     * REST 接口先把审核人的决定写入 checkpoint，再恢复 Graph 到这里。
     */
    public NodeAction humanReview() {
        return state -> {
            boolean approved = state.value(APPROVED, Boolean.class).orElse(false);
            String editedResponse = stringValue(state, EDITED_RESPONSE);
            String draft = stringValue(state, DRAFT_RESPONSE);
            String responseToSend = StringUtils.hasText(editedResponse) ? editedResponse : draft;

            if (approved && !StringUtils.hasText(responseToSend)) {
                throw new IllegalStateException("该邮件尚无草稿；审核通过时必须提交 editedResponse");
            }

            String nextNode = approved ? SEND_REPLY : END;
            Map<String, Object> reviewData = Map.of(
                    "approved", approved,
                    "reviewer_response", StringUtils.hasText(editedResponse) ? editedResponse : draft,
                    "reviewer_note", approved ? "审核通过" : "审核拒绝"
            );
            return Map.of(
                    REVIEW_DATA, reviewData,
                    NEXT_NODE, nextNode,
                    CURRENT_STEP, HUMAN_REVIEW,
                    STATUS, approved ? "APPROVED_FOR_SEND" : "REJECTED",
                    MESSAGES, List.of(approved ? "人工审核通过" : "人工审核拒绝，流程结束")
            );
        };
    }

    /**
     * 演示发送节点只记录“将要发送”的正文，不会真的发邮件。
     * 接入邮件服务时，应在这里调用外部服务并处理发送失败/重试。
     */
    public NodeAction sendReply() {
        return state -> {
            String edited = stringValue(state, EDITED_RESPONSE);
            String draft = stringValue(state, DRAFT_RESPONSE);
            String response = StringUtils.hasText(edited) ? edited : draft;
            if (!StringUtils.hasText(response)) {
                throw new IllegalStateException("没有可发送的回复正文");
            }

            return Map.of(
                    SENT_RESPONSE, response,
                    CURRENT_STEP, SEND_REPLY,
                    STATUS, "SENT",
                    MESSAGES, List.of("已将回复写入演示发送结果（没有调用真实邮件服务）")
            );
        };
    }

    private static EmailClassification normalize(EmailClassification raw) {
        if (raw == null) {
            throw new IllegalStateException("模型未返回邮件分类结果");
        }
        String intent = normalizeToken(raw.intent(), INTENTS, "other");
        String urgency = normalizeToken(raw.urgency(), URGENCIES, "medium");
        return new EmailClassification(
                intent,
                urgency,
                StringUtils.hasText(raw.topic()) ? raw.topic().strip() : "未分类主题",
                StringUtils.hasText(raw.summary()) ? raw.summary().strip() : "未提供摘要"
        );
    }

    private static String normalizeToken(String raw, Set<String> allowedValues, String fallback) {
        String normalized = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : fallback;
    }

    private static String chooseFirstNode(EmailClassification classification) {
        if ("billing".equals(classification.intent()) || "critical".equals(classification.urgency())) {
            return HUMAN_REVIEW;
        }
        return switch (classification.intent()) {
            case "question", "feature" -> SEARCH_DOCUMENTATION;
            case "bug" -> BUG_TRACKING;
            default -> DRAFT_RESPONSE_NODE;
        };
    }

    private static EmailClassification classification(OverAllState state) {
        return state.value(CLASSIFICATION, EmailClassification.class)
                .orElseThrow(() -> new IllegalStateException("共享状态中缺少邮件分类结果"));
    }

    private static String stringValue(OverAllState state, String key) {
        return state.value(key, String.class).orElse("");
    }

    private static List<String> stringListValue(OverAllState state, String key) {
        Object value = state.value(key).orElse(List.of());
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }
}
