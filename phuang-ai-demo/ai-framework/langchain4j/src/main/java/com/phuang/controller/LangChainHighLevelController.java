package com.phuang.controller;

import com.phuang.config.RedisChatMemoryStore;
import com.phuang.service.LangChainAiService;
import com.phuang.service.LangChainMemoryAiService;
import com.phuang.tool.TemperatureTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 *
 * @description LangChainHighLevelController
 * @author huangpeng
 * @since 2026/8/22
 */
@RequestMapping("/langchain4j/highLevel")
@RestController
public class LangChainHighLevelController implements InitializingBean {

    @Resource
    private LangChainAiService aiService;

    @Resource
    private OpenAiChatModel chatModel;

    @Resource
    private OpenAiStreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    private LangChainMemoryAiService langChainMemoryAiService;

    @RequestMapping("/chat")
    public String chat(@RequestParam("msg") String msg) {
        return aiService.chat(msg);
    }

    @RequestMapping("/chatStream")
    public Flux<String> chatStream(@RequestParam("msg") String msg) {
        return aiService.chatStream(msg);
    }

    @RequestMapping("/memoryChat")
    public String memoryChat(@RequestParam("memoryId") String memoryId,
                             @RequestParam("msg") String msg) {
        return langChainMemoryAiService.chatMemory(memoryId, msg);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        langChainMemoryAiService = AiServices.builder(LangChainMemoryAiService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(redisChatMemoryStore).build())
                .build();
    }

    @RequestMapping("/tool")
    public String toolCalling(@RequestParam("msg") String msg) {
        LangChainAiService langChainAiService = AiServices.builder(LangChainAiService.class)
                .tools(new TemperatureTools())
                .chatModel(chatModel)
                .build();
        return langChainAiService.chat(msg);
    }

}
