package com.phuang.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理后台对 Mem0 长期记忆进行查询、修改和删除的操作接口
 *
 * @description LongTermMemoryOperationController
 * @author huangpeng
 * @since 2026/9/25
 */
@RestController
@RequestMapping("/longTermMemory/operate")
public class LongTermMemoryOperationController {

    private final RestClient mem0Client;

    /**
     * 使用配置中的 Mem0 服务地址创建 REST 客户端
     *
     * @param restClientBuilder Spring 提供的 REST 客户端构建器
     * @param mem0BaseUrl Mem0 REST Server 的访问地址
     */
    public LongTermMemoryOperationController(RestClient.Builder restClientBuilder,
                                             @Value("${spring.ai.alibaba.mem0.client.base-url}") String mem0BaseUrl) {
        this.mem0Client = restClientBuilder.baseUrl(mem0BaseUrl).build();
    }

    /**
     * 按语义检索指定范围内的长期记忆
     *
     * @param request 检索条件，必须包含 query，且 userId、agentId、runId 至少传入一个
     * @return Mem0 返回的匹配记忆列表
     */
    @PostMapping("/search")
    public Object search(@RequestBody MemorySearchRequest request) {
        if (!StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query 不能为空");
        }
        requireScope(request.userId(), request.agentId(), request.runId());
        return mem0Client.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request.toMem0Request())
                .retrieve()
                .body(Object.class);
    }

    /**
     * 查询用户、Agent 或某次任务范围内的全部记忆
     *
     * @param userId 用户唯一标识
     * @param agentId Agent 唯一标识
     * @param runId 任务或会话唯一标识
     * @return Mem0 返回的记忆列表
     */
    @GetMapping("/memories")
    public Object getAll(@RequestParam(value = "userId", required = false) String userId,
                         @RequestParam(value = "agentId", required = false) String agentId,
                         @RequestParam(value = "runId", required = false) String runId) {
        requireScope(userId, agentId, runId);
        return mem0Client.get()
                .uri(uriBuilder -> uriBuilder.path("/memories")
                        .queryParamIfPresent("user_id", optional(userId))
                        .queryParamIfPresent("agent_id", optional(agentId))
                        .queryParamIfPresent("run_id", optional(runId))
                        .build())
                .retrieve()
                .body(Object.class);
    }

    /**
     * 根据记忆 ID 查询单条记忆详情。
     *
     * @param memoryId Mem0 记忆 ID
     * @return Mem0 返回的记忆详情
     */
    @GetMapping("/memories/{memoryId}")
    public Object get(@PathVariable String memoryId) {
        return mem0Client.get()
                .uri("/memories/{memoryId}", memoryId)
                .retrieve()
                .body(Object.class);
    }

    /**
     * 人工修改单条长期记忆。
     *
     * @param memoryId Mem0 记忆 ID
     * @param memory 要更新的记忆内容，例如 {"memory":"用户偏好 Java 技术方案"}
     * @return Mem0 返回的更新结果
     */
    @PutMapping("/memories/{memoryId}")
    public Object update(@PathVariable String memoryId, @RequestBody Map<String, Object> memory) {
        return mem0Client.put()
                .uri("/memories/{memoryId}", memoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(memory)
                .retrieve()
                .body(Object.class);
    }

    /**
     * 删除指定的一条长期记忆。
     *
     * @param memoryId Mem0 记忆 ID
     * @return Mem0 返回的删除结果
     */
    @DeleteMapping("/memories/{memoryId}")
    public Object delete(@PathVariable String memoryId) {
        return mem0Client.delete()
                .uri("/memories/{memoryId}", memoryId)
                .retrieve()
                .body(Object.class);
    }

    /**
     * 按用户、Agent 或任务范围批量删除长期记忆。
     *
     * @param userId 用户唯一标识
     * @param agentId Agent 唯一标识
     * @param runId 任务或会话唯一标识
     * @return Mem0 返回的批量删除结果
     */
    @DeleteMapping("/memories")
    public Object deleteAll(@RequestParam(value = "userId", required = false) String userId,
                            @RequestParam(value = "agentId", required = false) String agentId,
                            @RequestParam(value = "runId", required = false) String runId) {
        requireScope(userId, agentId, runId);
        return mem0Client.delete()
                .uri(uriBuilder -> uriBuilder.path("/memories")
                        .queryParamIfPresent("user_id", optional(userId))
                        .queryParamIfPresent("agent_id", optional(agentId))
                        .queryParamIfPresent("run_id", optional(runId))
                        .build())
                .retrieve()
                .body(Object.class);
    }

    /**
     * 校验批量操作和检索操作的记忆范围，防止误操作所有用户的记忆。
     */
    private void requireScope(String userId, String agentId, String runId) {
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(agentId) && !StringUtils.hasText(runId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少传入 userId、agentId、runId 之一");
        }
    }

    /**
     * 将非空参数转换为 URI 查询参数所需的 Optional。
     */
    private Optional<String> optional(String value) {
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    /**
     * 透传 Mem0 返回的业务错误状态码和响应内容。
     *
     * @param exception Mem0 返回的 4xx 或 5xx 异常
     * @return 与 Mem0 一致的错误响应
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleMem0Error(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        MediaType contentType = headers == null || headers.getContentType() == null
                ? MediaType.APPLICATION_JSON : headers.getContentType();
        return ResponseEntity.status(exception.getStatusCode())
                .contentType(contentType)
                .body(exception.getResponseBodyAsString());
    }

    /**
     * Mem0 服务不可访问时统一返回 502，便于管理后台区分业务错误与服务异常。
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<String> handleMem0Unavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Mem0 服务不可用");
    }

    /**
     * 管理后台的记忆检索请求。
     *
     * @param query 用于语义检索的问题或关键词
     * @param userId 用户唯一标识
     * @param agentId Agent 唯一标识
     * @param runId 任务或会话唯一标识
     * @param filters Mem0 元数据过滤条件
     * @param limit 最多返回的记忆条数
     */
    public record MemorySearchRequest(String query, String userId, String agentId, String runId,
                                      Map<String, Object> filters, Integer limit) {

        /**
         * 将管理后台使用的驼峰参数转换为 Mem0 REST API 使用的下划线参数。
         */
        Map<String, Object> toMem0Request() {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            if (StringUtils.hasText(userId)) {
                request.put("user_id", userId);
            }
            if (StringUtils.hasText(agentId)) {
                request.put("agent_id", agentId);
            }
            if (StringUtils.hasText(runId)) {
                request.put("run_id", runId);
            }
            if (filters != null) {
                request.put("filters", filters);
            }
            if (limit != null) {
                request.put("limit", limit);
            }
            return request;
        }
    }

}
