package com.sprouttrail.image.service;

import com.sprouttrail.image.config.ImageModelConfig;
import com.sprouttrail.image.config.ImageProperties;
import com.sprouttrail.image.exception.ImageWorkflowException;
import com.sprouttrail.image.model.GeneratedImage;
import com.sprouttrail.image.model.ImageAspectRatio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 完成图片生成、图片数据读取和服务器本地保存
 *
 * <p>该服务不记录用户的完整 prompt，也不记录模型返回的 base64 内容，
 * 防止日志意外保存用户数据或产生超大日志文件。
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    @Resource
    private ImageModelConfig imageModelConfig;

    @Resource
    private ImageProperties properties;

    private Path storageDirectory;

    private HttpClient httpClient;

    /**
     * 在依赖注入完成后初始化图片存储目录和 HTTP 客户端。
     *
     * <p>存储路径转换为绝对标准路径，后续生成的目标文件必须位于该目录内。
     */
    @PostConstruct
    private void initialize() {
        this.storageDirectory = properties.getStoragePath().toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 根据文字描述生成一张 PNG 图片并保存到本地目录
     *
     * @param prompt      已通过 Controller 校验的图片描述
     * @param aspectRatio 用户选择的图片画幅
     * @return 图片文件名及其二进制内容
     * @throws ImageWorkflowException 模型调用失败、返回内容无效或图片写入失败
     */
    public GeneratedImage generate(String prompt, ImageAspectRatio aspectRatio) {
        long startedAt = System.nanoTime();
        // 保留调用方传入的全部字符；提示词的前后空白也可能是用户有意输入的内容。
        Image image = callImageModel(prompt, aspectRatio);
        byte[] imageBytes = decodeImage(image);
        String filename = UUID.randomUUID() + ".png";
        Path target = storageDirectory.resolve(filename).normalize();

        // 文件名由服务端 UUID 生成；该检查继续保证未来修改文件名规则时不会发生路径穿越。
        if (!target.startsWith(storageDirectory)) {
            throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片已生成，但保存失败", new IllegalStateException("Invalid image target path"));
        }

        try {
            Files.createDirectories(storageDirectory);
            // CREATE_NEW 可以避免极端情况下同名文件被静默覆盖
            Files.write(target, imageBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片已生成，但保存失败", exception);
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info("Image generated and saved: filename={}, bytes={}, elapsedMs={}", filename, imageBytes.length, elapsedMillis);
        return new GeneratedImage(filename, imageBytes);
    }

    /**
     * 调用 LangChain4j，并把 SDK 或上游服务异常转换为稳定的业务异常
     */
    private Image callImageModel(String prompt, ImageAspectRatio aspectRatio) {
        try {
            ImageModel imageModel = imageModelConfig.imageModel(aspectRatio);
            Response<Image> response = imageModel.generate(prompt);
            Image image = response.content();
            if (image == null) {
                throw imageGenerationFailure();
            }
            return image;
        } catch (ImageWorkflowException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw imageGenerationFailure(exception);
        }
    }

    /**
     * 读取模型返回的图片数据。
     * OpenAI 兼容接口可能返回 base64，也可能返回短期有效的图片 URL。
     */
    private byte[] decodeImage(Image image) {
        String base64Data = image.base64Data();
        if (base64Data != null && !base64Data.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                if (bytes.length == 0) {
                    throw imageGenerationFailure();
                }
                return bytes;
            } catch (IllegalArgumentException exception) {
                throw imageGenerationFailure(exception);
            }
        }

        URI imageUrl = image.url();
        if (imageUrl == null) {
            throw imageGenerationFailure();
        }
        return downloadImage(imageUrl);
    }

    /**
     * 下载模型返回的图片 URL，并限制为 HTTPS，避免请求不安全的本地或明文地址
     */
    private byte[] downloadImage(URI imageUrl) {
        if (!"https".equalsIgnoreCase(imageUrl.getScheme())
                || imageUrl.getHost() == null
                || imageUrl.getRawUserInfo() != null) {
            throw imageGenerationFailure();
        }

        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(imageUrl)
                    .timeout(properties.getTimeout())
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw imageGenerationFailure(exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw imageGenerationFailure(exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw imageGenerationFailure();
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw imageGenerationFailure();
        }

        byte[] imageBytes = response.body();
        if (imageBytes == null || imageBytes.length == 0) {
            throw imageGenerationFailure();
        }
        return imageBytes;
    }

    private ImageWorkflowException imageGenerationFailure() {
        return new ImageWorkflowException("IMAGE_GENERATION_FAILED", HttpStatus.BAD_GATEWAY,
                "图片生成失败，请稍后重试");
    }

    private ImageWorkflowException imageGenerationFailure(Throwable cause) {
        return new ImageWorkflowException("IMAGE_GENERATION_FAILED", HttpStatus.BAD_GATEWAY,
                "图片生成失败，请稍后重试", cause);
    }
}
