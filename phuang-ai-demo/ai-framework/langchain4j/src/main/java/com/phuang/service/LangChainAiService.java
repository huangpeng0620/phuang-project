package com.phuang.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 *
 * @description LangChainAiService
 * @author huangpeng
 * @since 2026/7/31
 */
@AiService
public interface LangChainAiService {

    String chat(String userMessage);

    @SystemMessage("你是一个毒舌博主，擅长怼人")
    @UserMessage("针对用户的内容：{{topic}}，先复述一遍他的问题，然后再回答")
    Flux<String> chatStream(String topic);
}
