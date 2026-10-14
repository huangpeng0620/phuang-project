package com.sprouttrail.image.model.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprouttrail.image.config.ImageProperties;
import com.sprouttrail.image.exception.ImageWorkflowException;
import com.sprouttrail.image.model.enums.ImageAspectRatio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 为 LangChain4j 补充 Qwen 图片编辑协议，文生图继续委托原有模型实现。
 *
 * <p>Qwen 的兼容接口使用 JSON 的 image 扩展字段；LangChain4j 的 OpenAI edit()
 * 使用 /images/edits 和 multipart，两者不兼容，因此仅在这里适配编辑请求。
 * 图片、提示词和认证信息均不写入日志。
 */
@RequiredArgsConstructor
@Slf4j
public class QwenImageModel implements ImageModel {

    private final ImageModel generationModel;
    private final ImageProperties properties;
    private final ImageAspectRatio aspectRatio;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Override
    public Response<Image> generate(String prompt) {
        return generationModel.generate(prompt);
    }

    @Override
    public Response<List<Image>> generate(String prompt, int count) {
        return generationModel.generate(prompt, count);
    }

    /**
     * 将已校验的单张原图和用户编辑要求发送至 Qwen 兼容接口。
     *
     * @param image 已校验且包含 Base64 与实际 MIME 类型的原图
     * @param prompt 用户的编辑要求，不做 trim、拼接或自动改写
     * @return 模型返回的结果图片 URL，后续由 ImageService 下载并保存
     */
    @Override
    public Response<Image> edit(Image image, String prompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode()
                    .put("model", properties.getImageModel())
                    .put("prompt", prompt)
                    .put("image", "data:" + image.mimeType() + ";base64," + image.base64Data())
                    .put("size", aspectRatio.imageSize())
                    .put("n", 1)
                    // 用户已确认编辑要求，关闭模型侧的隐式提示词改写。
                    .put("prompt_extend", false);
            String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(properties.getTimeout())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            // 编辑请求不自动重试，避免超时后重复提交付费任务。
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Image edit request failed: status={}, model={}, requestId={}",
                        response.statusCode(), properties.getImageModel(),
                        response.headers().firstValue("x-request-id").orElse("unknown"));
                String message = switch (response.statusCode()) {
                    case 401, 403 -> "图片优化服务认证失败，请检查模型服务配置";
                    case 404 -> "图片优化模型或接口不存在，请检查模型服务配置";
                    case 429 -> "图片优化服务繁忙，请稍后重试";
                    default -> "图片优化失败，请检查图片和编辑要求后重试";
                };
                throw new ImageWorkflowException("IMAGE_EDIT_FAILED", HttpStatus.BAD_GATEWAY, message);
            }

            String url = objectMapper.readTree(response.body()).path("data").path(0).path("url").asText("");
            if (url.isBlank()) {
                throw editFailure(null);
            }
            return Response.from(Image.builder().url(URI.create(url)).mimeType("image/png").build());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw editFailure(exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw editFailure(exception);
        }
    }

    private ImageWorkflowException editFailure(Throwable cause) {
        return new ImageWorkflowException("IMAGE_EDIT_FAILED", HttpStatus.BAD_GATEWAY,
                "图片优化失败，请稍后重试", cause);
    }
}
