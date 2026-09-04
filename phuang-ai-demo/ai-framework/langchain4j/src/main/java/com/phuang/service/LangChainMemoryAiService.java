package com.phuang.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 *
 * @description LangChainMemoryAiService
 * @author huangpeng
 * @since 2026/8/22
 */
@AiService
public interface LangChainMemoryAiService {

    String chatMemory(@MemoryId String memoryId, @UserMessage String userMessage);

}
