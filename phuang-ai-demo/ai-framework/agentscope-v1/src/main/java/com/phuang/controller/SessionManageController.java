package com.phuang.controller;

import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 *
 * @description 会话管理
 * @author huangpeng
 * @since 2026/10/24
 */
@RestController
@RequestMapping("session/manage")
public class SessionManageController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @Resource
    private MysqlSession mysqlSession;

    @GetMapping("jsonSession/singleChat")
    public String jsonSessionSingleChat(@RequestParam("message") String message,
                                        @RequestParam("sessionId") String sessionId) {
        InMemoryMemory memory = new InMemoryMemory();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .memory(memory)
                .build();

        //当前项目的相对路径
        Path sessionPath = Path.of("ai-framework", "agentscope-v1", "jsonSession");
        Session jsonSession = new JsonSession(sessionPath);

        //加载会话
        reActAgent.loadIfExists(jsonSession, sessionId);

        // 调用agent
        Msg msg = reActAgent.call(Msg.builder()
                        .role(MsgRole.USER)
                        .textContent(message).build())
                .block();

        //保存会话
        reActAgent.saveTo(jsonSession, sessionId);
        return msg.getTextContent();
    }

    @GetMapping("mysqlSession/singleChat")
    public String mysqlSessionSingleChat(@RequestParam("message") String message,
                                         @RequestParam("sessionId") String sessionId) {
        InMemoryMemory memory = new InMemoryMemory();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .memory(memory)
                .build();

        //加载会话
        reActAgent.loadIfExists(mysqlSession, sessionId);

        // 调用agent
        Msg msg = reActAgent.call(Msg.builder()
                        .role(MsgRole.USER)
                        .textContent(message).build())
                .block();

        //保存会话
        reActAgent.saveTo(mysqlSession, sessionId);
        return msg.getTextContent();
    }

}
