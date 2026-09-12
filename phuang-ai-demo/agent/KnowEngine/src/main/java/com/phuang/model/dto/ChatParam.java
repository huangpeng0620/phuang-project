package com.phuang.model.dto;

import com.phuang.model.enums.ChatSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话处理参数
 * <p>
 *     用于在一次对话处理过程中传递用户、会话、消息、意图识别结果及消息来源等上下文信息
 * </p>
 * @author phuang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatParam {

    /** 用户 ID */
    private String userId;

    /** 会话 ID */
    private String conversationId;

    /** 当前用户消息 ID。 */
    private String messageId;

    /** 用户本次发送的消息内容  */
    private String content;

    /** 当前用户消息对应的助手消息 ID  */
    private String assistantMessageId;

    /** 用户问题的意图识别结果，用于选择提示词及后续处理策略  */
    private IntentRecognitionResult intentRecognitionResult;

    /** 对话来源 */
    private ChatSource chatSource;
}
