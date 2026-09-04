package com.phuang.controller;

import com.phuang.tool.TemperatureTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 *
 * @description LangChainLowLevelController
 * @author huangpeng
 * @since 2026/7/31
 */
@RequestMapping("/langchain4j/lowLevel")
@RestController
public class LangChainLowLevelController {

    @Resource
    private OpenAiChatModel chatModel;

    @Resource
    private OpenAiStreamingChatModel streamingChatModel;

    @RequestMapping("/hello")
    public String hello() {
        return chatModel.chat("你好,你是谁？");
    }

    @RequestMapping("/streamHello")
    public Flux<String> streamHello(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        Flux<String> flux = Flux.create(fluxSink -> {
            streamingChatModel.chat("你好,你是谁？", new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fluxSink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    fluxSink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    fluxSink.error(error);
                }
            });
        });
        return flux;
    }

    @RequestMapping("/memory")
    public String memory(HttpServletResponse response) {
        List<ChatMessage> messages = new ArrayList<>();

        //第一轮对话
        messages.add(new SystemMessage("你是一个AI助手"));
        messages.add(new UserMessage("我叫phuang，是一个程序员"));
        AiMessage answer = chatModel.chat(messages).aiMessage();
        System.out.println(answer);
        System.out.println("======");

        messages.add(answer);

        //第二轮对话
        messages.add(new UserMessage("phuang是干什么的?"));
        AiMessage answer1 = chatModel.chat(messages).aiMessage();
        System.out.println(answer1);
        System.out.println("======");

        messages.add(answer1);

        //第三轮对话
        messages.add(new UserMessage("我是谁？"));
        AiMessage answer2 = chatModel.chat(messages).aiMessage();
        System.out.println(answer2);
        System.out.println("======");
        return answer2.text();
    }

    @RequestMapping("/memory1")
    public String memory1(HttpServletResponse response) {
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        //第一轮对话
        chatMemory.add(new SystemMessage("你是一个AI助手"));
        chatMemory.add(new UserMessage("我叫phuang，是一个程序员"));
        AiMessage answer = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer);
        System.out.println("======");

        chatMemory.add(answer);

        //第二轮对话
        chatMemory.add(new UserMessage("phuang是干什么的?"));

        AiMessage answer1 = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer1);
        System.out.println("======");

        chatMemory.add(answer1);

        //第三轮对话
        chatMemory.add(new UserMessage("我是谁？"));
        AiMessage answer2 = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer2);
        System.out.println("======");

        return answer2.text();
    }

    @RequestMapping("tool")
    public String tool() {
        //1、定义工具列表
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(TemperatureTools.class);
        //2.构造用户提示词
        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = UserMessage.from("2025年11月11日，杭州的气温怎样？");
        chatMessages.add(userMessage);
        //3. 创建ChatRequest，并指定工具列表
        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .toolChoice(ToolChoice.AUTO)
                .build();
        //4. 调用模型
        ChatResponse response = chatModel.chat(request);
        AiMessage aiMessage = response.aiMessage();
        //5.把模型结果添加到chatMessages中
        chatMessages.add(aiMessage);

        //6.执行工具(低层次API中大模型不会帮我们执行工具)
        List<ToolExecutionRequest> toolExecutionRequests = response.aiMessage().toolExecutionRequests();
        toolExecutionRequests.forEach(toolExecutionRequest -> {
            ToolExecutor toolExecutor = new DefaultToolExecutor(new TemperatureTools(), toolExecutionRequest);
            System.out.println("execute tool " + toolExecutionRequest.name());
            String result = toolExecutor.execute(toolExecutionRequest, UUID.randomUUID().toString());
            ToolExecutionResultMessage toolExecutionResultMessages = ToolExecutionResultMessage.from(toolExecutionRequest, result);
            //7.把工具执行结果添加到chatMessages中
            chatMessages.add(toolExecutionResultMessages);
        });

        //8.重新构造ChatRequest，并使用之前的对话chatMessages，以及指定toolSpecifications
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        //9. 调用模型
        ChatResponse finalChatResponse = chatModel.chat(finalRequest);
        return finalChatResponse.aiMessage().text();
    }

}
