package com.phuang.controller;

import com.phuang.tool.WeatherTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 *
 * @description 模型集成示例
 * @author huangpeng
 * @since 2026/10/24
 */
@RestController
@RequestMapping("workspace")
public class WorkspaceController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("singleChat")
    public String singleChat(@RequestParam("message") String message,
                             @RequestParam(value = "sessionId", required = false) String sessionId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        String workspacePath = System.getProperty("user.dir") + "/ai-framework/agentscope-v2/workspace";

        HarnessAgent harnessAgent = HarnessAgent.builder()
                .name("harnessAgent")
                .model(qianwenChatModel)
                .agentId("harnessAgent")
                .toolkit(toolkit)
                .workspace(workspacePath)
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .userId("phuang")
                .sessionId(sessionId)
                .build();
        Msg msg = harnessAgent.call(message, ctx).block();
        return msg.getTextContent();
    }
}
