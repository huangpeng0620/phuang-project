package com.phuang.service.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface KnowEngineChatAiService {

    Flux<String> streamChat(@MemoryId String conversationId, @UserMessage String message);

    String chat(@MemoryId String conversationId, @UserMessage String message);

}
