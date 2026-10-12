package com.sprouttrail.image.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 图片生成相关配置
 *
 * <p>配置前缀为 {@code app.image}。敏感的 API Key 只允许通过服务器环境变量注入，
 * 不应写入代码、前端文件或提交到 Git。
 *
 * @param apiKey      图片模型服务的 API Key
 * @param baseUrl     OpenAI 或兼容服务的 API 根地址
 * @param model       图片模型名称
 * @param size        生成图片尺寸，MVP 固定为 1024x1024
 * @param quality     图片质量
 * @param storagePath 图片在服务器上的保存目录
 * @param timeout     单次图片生成的最大等待时间
 */
@Validated
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotBlank String size,
        @NotBlank String quality,
        @NotNull Path storagePath,
        @NotNull Duration timeout) {
}