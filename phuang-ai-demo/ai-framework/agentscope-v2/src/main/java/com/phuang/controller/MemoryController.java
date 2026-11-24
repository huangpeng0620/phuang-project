package com.phuang.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @description MemoryController
 * @author huangpeng
 * @since 2026/11/1
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @Resource
    private MysqlAgentStateStore mysqlAgentStateStore;

    @Resource
    private JdbcDistributedStore jdbcDistributedStore;

    @GetMapping("/chat")
    public String memoryChat(@RequestParam(value = "message", required = false) String message,
                             @RequestParam(value = "sessionId", required = false) String sessionId) {

        String workspacePath = System.getProperty("user.dir") + "/ai-framework/agentscope-v2/workspace/harnessAgent";

        HarnessAgent harnessAgent = HarnessAgent.builder()
                .name("harnessAgent")
                .agentId("harnessAgent")
                .sysPrompt("You are a assistant.")
                .model(qianwenChatModel)
                //.middlewares()
                .workspace(workspacePath)
                //.legacySessionHistory(Boolean.TRUE)
                //.stateStore(new JsonFileAgentStateStore(Paths.get(System.getProperty("user.dir"), "ai-framework", "agentscope-v2", "state", "harnessAgent")))
                //.stateStore(mysqlAgentStateStore)
                .distributedStore(jdbcDistributedStore)
                .filesystem(new RemoteFilesystemSpec())
                .toolResultEviction(
                        ToolResultEvictionConfig.builder()
                                .maxResultChars(40_000)
                                .previewChars(1_000)
                                .evictionPath("large_tool_results")
                                .build())
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId("phuang")
                .build();

        return harnessAgent
                .call(message, ctx)
                .block()
                .getTextContent();
    }
}
