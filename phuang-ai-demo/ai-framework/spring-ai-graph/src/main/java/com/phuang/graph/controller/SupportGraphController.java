package com.phuang.graph.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static com.phuang.graph.config.SupportGraphConfiguration.QUESTION;

@RestController
@RequestMapping("/api/graph")
public class SupportGraphController {

    @Resource(name = "supportGraph")
    private CompiledGraph supportGraph;

    @PostMapping("/run")
    public GraphRunResponse run(@RequestBody GraphRunRequest request) {
        // 执行和后续查询必须使用相同 threadId，才能命中同一份 checkpoint。
        RunnableConfig config = configFor(request.threadId());
        NodeOutput output = supportGraph.invokeAndGetOutput(Map.of(QUESTION, request.question()), config)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Graph 未返回运行结果"));
        return new GraphRunResponse(
                request.threadId(),
                output.node(),
                output.state().data()
        );
    }

    private RunnableConfig configFor(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "threadId 不能为空");
        }
        return RunnableConfig.builder()
                .threadId(threadId)
                // metadata 是本次运行的附加上下文，可用于日志、观测或节点中的业务判断。
                .addMetadata("source", "rest-api")
                .build();
    }

    public record GraphRunRequest(String threadId, String question) {
    }

    public record GraphRunResponse(String threadId, String node, Map<String, Object> state) {
    }
}
