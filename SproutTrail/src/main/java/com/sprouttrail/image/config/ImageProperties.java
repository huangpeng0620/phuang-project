package com.sprouttrail.image.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 图片生成相关配置
 *
 * <p>配置前缀为 {@code app.image}。敏感的 API Key 只允许通过服务器环境变量注入，
 * 不应写入代码、前端文件或提交到 Git。
 */
@Validated
@ConfigurationProperties(prefix = "app.image")
@Getter
@Setter
@NoArgsConstructor
public class ImageProperties {

    /** 图片模型服务的 API Key。 */
    @NotBlank
    private String apiKey;

    /** OpenAI 或兼容服务的 API 根地址。 */
    @NotBlank
    private String baseUrl;

    /** 图片模型名称。 */
    @NotBlank
    private String imageModel;

    /** 提示词优化使用的文本模型名称。 */
    @NotBlank
    private String promptModel;

    /** 提示词优化的兼容接口地址；为空字符串时复用图片接口地址。 */
    @NotNull
    private String promptBaseUrl;

    /** 图片在服务器上的保存目录。 */
    @NotNull
    private Path storagePath;

    /** 单次图片生成的最大等待时间。 */
    @NotNull
    private Duration timeout;
}
