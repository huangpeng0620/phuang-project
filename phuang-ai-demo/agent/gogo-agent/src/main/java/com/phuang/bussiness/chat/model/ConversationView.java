package com.phuang.bussiness.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @description ConversationView
 * @author huangpeng
 * @since 2026/11/25
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationView {

    private String id;

    private String title;

    private long createdAt;

    private long updatedAt;
}
