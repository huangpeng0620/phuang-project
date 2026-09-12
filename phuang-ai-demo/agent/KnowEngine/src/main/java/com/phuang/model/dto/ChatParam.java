package com.phuang.model.dto;


import com.phuang.model.enums.ChatSource;

public record ChatParam(String userId,
                        String conversationId,
                        String messageId,
                        String content,
                        String assistantMessageId,
                        IntentRecognitionResult intentRecognitionResult,
                        ChatSource chatSource) {
}
