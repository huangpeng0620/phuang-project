package com.phuang.controller;

import com.phuang.config.DashScopeChatModelConfig;
import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.mysql.MysqlSession;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 *
 * @description MemoryController
 * @author huangpeng
 * @since 2026/11/1
 */
@RestController
@RequestMapping("long/memory")
public class LongMemoryController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @Resource
    private DashScopeChatModelConfig dashScopeChatModelConfig;

    @Resource
    private MysqlSession mysqlSession;

    @GetMapping("/bailian/chat")
    public String bailianLongMemoryChat(@RequestParam(value = "message", required = false) String message,
                                        @RequestParam(value = "sessionId", required = false) String sessionId) {

        InMemoryMemory memory = new InMemoryMemory();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());

        BailianLongTermMemory bailianLongTermMemory = BailianLongTermMemory.builder()
                .apiKey(dashScopeChatModelConfig.getApiKey())
                .userId("phuang")
                .memoryLibraryId(dashScopeChatModelConfig.getMemoryLibraryId())
                .projectId(null)
                .profileSchema(null)
                .metadata(Map.of("location_name", "shaoxing"))
                .build();

        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .memory(memory)
                .longTermMemory(bailianLongTermMemory)
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .build();

        //加载会话
        reActAgent.loadIfExists(mysqlSession, sessionId);

        // 调用 agent
        Msg msg = reActAgent.call(Msg.builder()
                        .role(MsgRole.USER)
                        .textContent(message).build())
                .block();

        //保存会话
        reActAgent.saveTo(mysqlSession, sessionId);
        return msg.getTextContent();
    }
}
