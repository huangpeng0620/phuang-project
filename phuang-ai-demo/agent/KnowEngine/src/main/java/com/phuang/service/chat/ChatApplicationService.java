package com.phuang.service.chat;

import com.phuang.model.enums.ChatSource;
import reactor.core.publisher.Flux;

/**
 *
 * @description ChatApplicationService
 * @author huangpeng
 * @since 2026/9/6
 */

public interface ChatApplicationService {

    Flux<String> chat(String userId, String content, String conversationId, ChatSource chatSource);

}
