package com.phuang.config;

import com.alibaba.fastjson2.JSON;
import com.phuang.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 *
 * @description StreamingController
 * @author huangpeng
 * @since 2026/10/24
 */
@RestController
@RequestMapping("stream/output")
public class StreamingController {

    @Resource
    private DashScopeChatModel qianwenChatModel;

    @GetMapping("singleChat")
    public Flux<String> singleChat(@RequestParam("message") String message) {

        //注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());

        //构建智能体
        ReActAgent reActAgent = ReActAgent.builder()
                .name("reActAgent")
                .model(qianwenChatModel)
                .toolkit(toolkit)
                .build();

        // 配置流式选项
        StreamOptions streamOptions = StreamOptions.builder()
                // 选择要接收的事件类型
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.AGENT_RESULT)
                // true = 增量模式（只发送新增内容）、false = 累积模式（每次发送全部已累积内容）
                .incremental(true)
                // 是否包含最终推理结果
                .includeReasoningChunk(true)
                .includeReasoningResult(true)
                .includeSummaryChunk(true)
                .includeSummaryResult(true)
                .build();

        //构建用户消息
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(message).build();

        // 调用 stream() 获取事件流
        return reActAgent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic())
                .map(event -> JSON.toJSONString(event.getMessage()))
                .filter(text -> text != null && !text.isEmpty())
                // 当流式处理发生异常时，不直接中断请求，而是返回一条 JSON 格式的错误消息
                .onErrorResume(error -> Flux.just(JSON.toJSONString(Map.of(
                        "event", "agent_error",
                        "data", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()))));
    }
}
