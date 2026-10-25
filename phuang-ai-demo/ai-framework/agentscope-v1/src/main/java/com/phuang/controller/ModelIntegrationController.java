package com.phuang.controller;

import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${dash-scope.chat-model.api-key}")
    private String apiKey;

    @Value("${dash-scope.chat-model.url}")
    private String url;

    @Value("${dash-scope.chat-model.qianwen-model-name}")
    private String qianwenModelName;

    @GetMapping("singleChat")
    public String singleChat(@RequestParam("message") String message) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());

        DashScopeChatModel dashScopeChatModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(url)
                .modelName(qianwenModelName)
                .build();
        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(dashScopeChatModel)
                .toolkit(toolkit)
                .build();
        Msg msg = reActAgent.call(Msg.builder().textContent(message).build())
                .block();
        return msg.getTextContent();
    }

}
