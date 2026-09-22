package com.phuang.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.phuang.tools.WeatherService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 *
 * @description HITLController
 * @author huangpeng
 * @since 2026/9/22
 */
@RestController
@RequestMapping("/react/hitl")
public class HITLController {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message,
                       @RequestParam("conversationId") String conversationId) {

        //配置中断
        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("getWeather", ToolConfig.builder()
                        .description("请确认操作")
                        .build())
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("HITL_AGENT")
                .model(deepSeekR1ChatModel)
                .saver(new MemorySaver())
                .tools(ToolCallbacks.from(new WeatherService()))
                .hooks(List.of(humanInTheLoopHook))
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        return null;
    }

}
