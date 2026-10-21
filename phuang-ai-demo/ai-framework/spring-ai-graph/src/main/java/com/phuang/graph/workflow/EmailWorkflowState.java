package com.phuang.graph.workflow;

/** 工作流中共享状态的字段名和节点名集中定义在这里，避免字符串散落各处。 */
public final class EmailWorkflowState {

    private EmailWorkflowState() {
    }

    // 输入：一封待处理邮件的基本信息。
    public static final String EMAIL_ID = "email_id";
    public static final String SENDER_EMAIL = "sender_email";
    public static final String EMAIL_CONTENT = "email_content";

    // 各节点产出的业务数据。
    public static final String CLASSIFICATION = "classification";
    public static final String CUSTOMER_HISTORY = "customer_history";
    public static final String SEARCH_RESULTS = "search_results";
    public static final String TICKET_ID = "ticket_id";
    public static final String DRAFT_RESPONSE = "draft_response";
    public static final String EDITED_RESPONSE = "edited_response";
    public static final String SENT_RESPONSE = "sent_response";
    public static final String REVIEW_DATA = "review_data";
    public static final String APPROVED = "approved";

    // 路由/运行信息：条件边读取 NEXT_NODE，MESSAGES 记录节点执行说明。
    public static final String NEXT_NODE = "next_node";
    public static final String CURRENT_STEP = "current_step";
    public static final String STATUS = "status";
    public static final String MESSAGES = "messages";

    // Graph 节点名称也作为常量暴露给编译配置和 REST 接口使用。
    public static final String READ_EMAIL = "read_email";
    public static final String CLASSIFY_INTENT = "classify_intent";
    public static final String SEARCH_DOCUMENTATION = "search_documentation";
    public static final String BUG_TRACKING = "bug_tracking";
    public static final String DRAFT_RESPONSE_NODE = "draft_response";
    public static final String HUMAN_REVIEW = "human_review";
    public static final String SEND_REPLY = "send_reply";
}
