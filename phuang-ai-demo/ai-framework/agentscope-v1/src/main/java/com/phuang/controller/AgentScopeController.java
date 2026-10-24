package com.phuang.controller;

import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description AgentScopeController
 * @author huangpeng
 * @since 2026/10/24
 */
@RestController
@RequestMapping("agentScope/agent")
public class AgentScopeController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("singleChat")
    public String singleChat(@RequestParam("message") String message) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());

        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .sysPrompt("你是一个个人助手")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .build();
        Msg msg = reActAgent.call(Msg.builder().textContent(message).build())
                .block();
        return msg.getTextContent();
    }

}
