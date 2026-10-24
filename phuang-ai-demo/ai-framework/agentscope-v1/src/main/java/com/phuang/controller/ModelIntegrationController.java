package com.phuang.controller;

import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @description 模型集成示例
 * @author huangpeng
 * @since 2026/10/24
 */
@RestController
@RequestMapping("model/integration")
public class ModelIntegrationController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("singleChat")
    public String singleChat(@RequestParam("message") String message) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .build();
        Msg msg = reActAgent.call(Msg.builder()
                        .textContent(message).build())
                .block();
        return msg.getTextContent();
    }

}
