package com.phuang.graph.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.phuang.graph.workflow.EmailWorkflowState.*;

/** 通过 HTTP 启动邮件工作流、查看 checkpoint，并提交人工审核结果。 */
@RestController
@RequestMapping("/api/graph")
public class SupportGraphController {

    @Resource(name = "supportGraph")
    private CompiledGraph supportGraph;

    /** 启动一封新邮件的工作流；Graph 遇到人工审核节点时会暂停并返回当前状态。 */
    @PostMapping("/run")
    public GraphStateResponse run(@RequestBody GraphRunRequest request) {
        requireText(request.threadId(), "threadId");
        requireText(request.emailId(), "emailId");
        requireText(request.senderEmail(), "senderEmail");
        requireText(request.emailContent(), "emailContent");

        // threadId 是 checkpoint 的会话键；查询状态或恢复运行时必须复用同一个值。
        RunnableConfig config = configFor(request.threadId());
        Map<String, Object> initialState = Map.of(
                EMAIL_ID, request.emailId(),
                SENDER_EMAIL, request.senderEmail(),
                EMAIL_CONTENT, request.emailContent(),
                MESSAGES, List.of()
        );

        // blockLast 等待本次可运行部分结束；若在 human_review 前中断，也会正常返回。
        supportGraph.stream(initialState, config).blockLast();
        return currentState(request.threadId());
    }

    /** 读取指定邮件会话当前 checkpoint，适合查看工作流暂停在哪个节点/等待哪一步。 */
    @GetMapping("/state/{threadId}")
    public GraphStateResponse getState(@PathVariable String threadId) {
        return currentState(threadId);
    }

    /** 返回同一个 threadId 下保存的所有执行快照，便于学习每个节点如何更新共享状态。 */
    @GetMapping("/history/{threadId}")
    public List<GraphStateResponse> getHistory(@PathVariable String threadId) {
        RunnableConfig config = configFor(threadId);
        List<GraphStateResponse> history = new ArrayList<>();
        supportGraph.getStateHistory(config).forEach(snapshot -> history.add(toResponse(threadId, snapshot)));
        return history;
    }

    /**
     * 提交审核决定并恢复同一个 Graph 执行。
     * 账单/critical 邮件可能在草稿生成前进入审核，这种情况下通过审核必须同时提交 editedResponse。
     */
    @PostMapping("/{threadId}/review")
    public GraphStateResponse review(@PathVariable String threadId, @RequestBody ReviewRequest request)
            throws Exception {
        RunnableConfig config = configFor(threadId);
        StateSnapshot waitingSnapshot = supportGraph.stateOf(config)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该 threadId 的 Graph 状态"));

        if (!HUMAN_REVIEW.equals(waitingSnapshot.next())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前 Graph 并未暂停在人工审核节点，next=" + waitingSnapshot.next());
        }

        String editedResponse = request.editedResponse();
        boolean hasDraft = StringUtils.hasText((String) waitingSnapshot.state().data().get(DRAFT_RESPONSE));
        if (request.approved() && !hasDraft && !StringUtils.hasText(editedResponse)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "这封邮件还没有回复草稿；审核通过时请提供 editedResponse");
        }

        // 只把审核人提供的增量写入 checkpoint；不需要重传原始邮件或已有分类结果。
        Map<String, Object> reviewUpdate = new LinkedHashMap<>();
        reviewUpdate.put(APPROVED, request.approved());
        if (StringUtils.hasText(editedResponse)) {
            reviewUpdate.put(EDITED_RESPONSE, editedResponse.strip());
        }

        // updateState 返回带有更新 checkpoint 的配置；stream(null, ...) 从暂停点继续执行。
        RunnableConfig resumedConfig = supportGraph.updateState(config, reviewUpdate, null);
        supportGraph.stream(null, resumedConfig).blockLast();
        return currentState(threadId);
    }

    private GraphStateResponse currentState(String threadId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        StateSnapshot snapshot = supportGraph.stateOf(config)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该 threadId 的 Graph 状态"));
        return toResponse(threadId, snapshot);
    }

    private GraphStateResponse toResponse(String threadId, StateSnapshot snapshot) {
        return new GraphStateResponse(threadId, snapshot.node(), snapshot.next(), snapshot.state().data());
    }

    private RunnableConfig configFor(String threadId) {
        requireText(threadId, "threadId");
        return RunnableConfig.builder()
                .threadId(threadId)
                // metadata 是附加运行上下文，与 Graph 的共享 state/checkpoint 是两类不同数据。
                .addMetadata("source", "rest-api")
                .build();
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
    }

    public record GraphRunRequest(String threadId, String emailId, String senderEmail, String emailContent) {
    }

    public record ReviewRequest(boolean approved, String editedResponse) {
    }

    public record GraphStateResponse(String threadId, String node, String nextNode, Map<String, Object> state) {
    }
}
