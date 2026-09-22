package com.phuang.handler.rag.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * 调用 JEV 结构化决策接口，为查询选择检索策略。
 * <p>
 * 该客户端只负责 HTTP 通信和响应校验，不负责将策略映射为具体的 Retriever
 */
@Component
public class JevRouteClient {

    private static final String ROUTE_QUESTION_ID = "route_strategy";
    private static final Set<String> SUPPORTED_STRATEGIES = Set.of(
            "relational_db", "graph_db", "knowledge_base"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    @Getter
    private final boolean enabled;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final double minConfidence;

    /**
     * 创建由 Spring 管理的 JEV 客户端，并从配置中读取 HTTP 调用与路由校验参数。
     * <p>
     * 此处创建的 {@link HttpClient} 仅设置连接超时；单次请求的整体超时由 {@code jev.route.timeout} 控制。
     *
     * @param objectMapper JSON 序列化和反序列化工具
     * @param enabled 是否启用 JEV 路由
     * @param endpoint JEV 决策接口的完整请求地址
     * @param apiKey 调用 JEV 接口的认证密钥
     * @param model JEV 使用的模型标识
     * @param timeout 单次 HTTP 请求的整体超时时间
     * @param minConfidence 可接受路由结果的最低置信度
     */
    public JevRouteClient(ObjectMapper objectMapper,
                          @Value("${jev.route.enabled:false}") boolean enabled,
                          @Value("${jev.route.endpoint:https://api.typesafe.ai/v1/systemone}") String endpoint,
                          @Value("${jev.route.api-key:}") String apiKey,
                          @Value("${jev.route.model:typesafe-ai/jev}") String model,
                          @Value("${jev.route.timeout:1500ms}") Duration timeout,
                          @Value("${jev.route.min-confidence:0.70}") double minConfidence) {
        this(objectMapper, HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .build(),
                enabled, endpoint, apiKey, model, timeout, minConfidence);
    }

    /**
     * 创建可注入自定义 {@link HttpClient} 的客户端，供单元测试或特殊运行环境复用。
     *
     * @param objectMapper JSON 序列化和反序列化工具
     * @param httpClient 用于发送 JEV HTTP 请求的客户端
     * @param enabled 是否启用 JEV 路由
     * @param endpoint JEV 决策接口的完整请求地址
     * @param apiKey 调用 JEV 接口的认证密钥
     * @param model JEV 使用的模型标识
     * @param timeout 单次 HTTP 请求的整体超时时间
     * @param minConfidence 可接受路由结果的最低置信度
     */
    JevRouteClient(ObjectMapper objectMapper,
                   HttpClient httpClient,
                   boolean enabled,
                   String endpoint,
                   String apiKey,
                   String model,
                   Duration timeout,
                   double minConfidence) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.enabled = enabled;
        this.endpoint = normalizeEndpoint(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null ? "" : model.strip();
        this.timeout = timeout;
        this.minConfidence = minConfidence;
    }

    /**
     * 调用 JEV 的 Choice 决策接口，返回已通过策略白名单和最低置信度校验的路由结果。
     * <p>
     * 方法会构造汽车领域的三选一检索策略请求，携带 Bearer 认证发送 HTTP 请求，并解析响应；
     * 非 2xx 状态、网络异常、非法策略或置信度不足均会抛出异常，由上层路由器执行降级处理。
     *
     * @param queryText 用于判断检索数据源的查询文本
     * @return 结构化路由决策
     */
    public RouteDecision route(String queryText) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("JEV 路由已启用，但未配置 jev.route.api-key");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createRequestBody(queryText))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("JEV 路由请求失败，HTTP 状态码：" + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 JEV 路由时线程被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("调用 JEV 路由失败", e);
        }
    }

    /**
     * 构造符合 JEV Choice 决策接口格式的请求体。
     * <p>
     * 将用户问题放入 {@code state.query}，并预先定义三种候选检索策略及其适用范围，
     * 使模型只能在关系型数据库、图数据库和知识库之间选择。
     *
     * @param queryText 用户原始查询
     * @return 可直接序列化为 JSON 的请求体
     */
    private ObjectNode createRequestBody(String queryText) {
        ObjectNode request = objectMapper.createObjectNode();
        if (!model.isBlank()) {
            request.put("model", model);
        }

        ObjectNode state = request.putObject("state");
        state.put("query", queryText);
        state.put("domain", "automotive");

        ObjectNode question = request.putObject("questions").putObject(ROUTE_QUESTION_ID);
        question.put("type", "choice");
        question.put("instructions", "选择最适合处理该汽车领域查询的唯一检索数据源。");
        ObjectNode criteria = question.putObject("criteria");
        criteria.put("relational_db", "用户车辆、订单、保险、保养记录等结构化属性、时间、数值或聚合查询。");
        criteria.put("graph_db", "车型、部件、配置等实体之间的关系、路径、层级或关联查询。");
        criteria.put("knowledge_base", "售前售后、故障排查、政策说明、操作指导等非结构化知识问答。");
        return request;
    }

    /**
     * 解析并校验 JEV 决策响应
     * <p>
     * 从 TypeSafe 顶层 {@code answers} 中读取路由结果，并仅接受预定义白名单内的策略和不低于配置阈值的置信度，
     * 防止异常响应或模型自由文本直接影响 Retriever 选择。
     *
     * @param responseBody JEV 返回的 JSON 响应体
     * @return 通过校验的路由策略与置信度
     * @throws IOException 响应体不是合法 JSON 时抛出
     * @throws IllegalStateException 接口返回失败、策略不受支持或置信度不足时抛出
     */
    RouteDecision parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode answer = root.path("answers").path(ROUTE_QUESTION_ID);
        String strategy = answer.path("choice").asText();
        double confidence = answer.path("confidence").asDouble(Double.NaN);

        if (!SUPPORTED_STRATEGIES.contains(strategy)) {
            throw new IllegalStateException("JEV 返回了不支持的路由策略：" + strategy);
        }
        if (!Double.isFinite(confidence) || confidence < minConfidence) {
            throw new IllegalStateException("JEV 路由置信度不足：" + confidence);
        }
        return new RouteDecision(strategy, confidence);
    }

    /**
     * 校验并清理配置的完整 JEV 接口地址
     *
     * @param endpoint 配置的接口地址
     * @return 去除首尾空白后的接口地址
     */
    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("JEV endpoint 不能为空");
        }
        return endpoint.strip();
    }

    /**
     * JEV 返回并通过本地校验的路由决策
     *
     * @param strategy 检索策略标识
     * @param confidence 模型对该策略的置信度
     */
    public record RouteDecision(String strategy, double confidence) {
    }
}
