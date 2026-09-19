package com.phuang.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.phuang.tools.StockTools;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 * @description ReactAgentController
 * @author huangpeng
 * @since 2026/9/18
 */
@RestController
@RequestMapping("/react")
public class ReactAgentController {

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private ChatModel chatModel;

    @Resource
    private ToolCallingManager toolCallingManager;

    @GetMapping("/chat")
    public String chat(String conversationId) {
        //定义ChatOptions
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                //指定工具
                .toolCallbacks(ToolCallbacks.from(new StockTools()))
                //指定不自动执行工具
                .internalToolExecutionEnabled(false)
                .build();

        //定义提示词，要求按照React架构运行
        Prompt prompt = new Prompt(
                List.of(new SystemMessage("""
                                你是一个基于React架构（Reasoning-Act-Observation）的智能助手，你擅长使用工具帮我解决问题，你的工作流程是：
                                1、思考：先根据用户的提问进行思考，推理出下一步需要进行的具体系统
                                2、行动：做具体的行动，这一步可以使用工具
                                3、观察：记录前一步行动的结果。你可以进行多轮思考和行动。如果要使用工具，请务必调用工具，不要自己随便捏造结果；
                                """),
                        new UserMessage("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。")),
                chatOptions);

        //添加提示词到记忆
        chatMemory.add(conversationId, prompt.getInstructions());

        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);

        //调用模型
        ChatResponse chatResponse = chatModel.call(promptWithMemory);

        //添加模型返回结果到记忆
        chatMemory.add(conversationId, chatResponse.getResult().getOutput());

        //循环处理工具调用
        while (chatResponse.hasToolCalls()) {
            //执行工具调用
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
                    chatResponse);

            //添加工具调用结果到记忆
            chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
                    .get(toolExecutionResult.conversationHistory().size() - 1));

            //创建新的提示词
            promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);

            //调用模型
            chatResponse = chatModel.call(promptWithMemory);

            //添加模型返回结果到记忆
            chatMemory.add(conversationId, chatResponse.getResult().getOutput());
        }

        for (Message message : chatMemory.get(conversationId)) {
            System.out.println(message);
        }

        return chatResponse.getResult().getOutput().getText();
    }

    @GetMapping("/chat1")
    public String chat1(String conversationId) throws GraphRunnerException {
        String systemPrompt = String.format("""
                你是一个基于React架构（Reasoning-Act-Observation）的智能助手，你擅长使用工具帮我解决问题，你的工作流程是：
                1、思考：先根据用户的提问进行思考，推理出下一步需要进行的具体系统
                2、行动：做具体的行动，这一步可以使用工具
                3、观察：记录前一步行动的结果。你可以进行多轮思考和行动。如果要使用工具，请务必调用工具，不要自己随便捏造结果；
                """);

        ReactAgent agent = ReactAgent.builder()
                .name("executor")
                .model(chatModel)
                .tools(ToolCallbacks.from(new StockTools()))
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        AssistantMessage chatResponse = agent.call("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。", config);
        return chatResponse.getText();
    }
}
