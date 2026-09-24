package com.phuang.controller;

import com.alibaba.cloud.ai.memory.mem0.advisor.Mem0ChatMemoryAdvisor;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.alibaba.cloud.ai.memory.mem0.advisor.Mem0ChatMemoryAdvisor.USER_ID;

@Lazy
@RestController
@RequestMapping("/longTermMemory")
public class LongTermMemoryController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    @Resource
    private VectorStore mem0MemoryStore;

    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message,
                       @RequestParam("userId") String userId) {
        return chatClient.prompt(message)
                .advisors(req -> req.params(Map.of(USER_ID, userId)))
                .call().content();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Mem0ChatMemoryAdvisor mem0ChatMemoryAdvisor = Mem0ChatMemoryAdvisor.builder(mem0MemoryStore).build();
        this.chatClient = ChatClient.builder(deepSeekR1ChatModel)
                .defaultAdvisors(mem0ChatMemoryAdvisor)
                .build();
    }
}