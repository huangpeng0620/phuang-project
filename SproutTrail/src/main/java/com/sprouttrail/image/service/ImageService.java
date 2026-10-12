package com.sprouttrail.image.service;

import com.sprouttrail.image.config.ImageProperties;
import com.sprouttrail.image.exception.ImageGenerationException;
import com.sprouttrail.image.exception.ImageStorageException;
import com.sprouttrail.image.model.GeneratedImage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
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
import java.time.Duration;
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

    private final ImageModel imageModel;

    private final Path storageDirectory;

    private final HttpClient httpClient;

    private final Duration timeout;

    /**
     * 创建图片服务
     *
     * <p>存储路径在构造时转换为绝对标准路径，后续生成的目标文件必须位于该目录内
     *
     * @param imageModel LangChain4j 图片模型
     * @param properties 图片生成配置
     */
    public ImageService(ImageModel imageModel, ImageProperties properties) {
        this.imageModel = imageModel;
        this.storageDirectory = properties.storagePath().toAbsolutePath().normalize();
        this.timeout = properties.timeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 根据文字描述生成一张 PNG 图片并保存到本地目录
     *
     * @param prompt 已通过 Controller 校验的图片描述
     * @return 图片文件名及其二进制内容
     * @throws ImageGenerationException 模型调用失败、返回空内容或图片数据无效
     * @throws ImageStorageException    图片写入本地目录失败
     */
    public GeneratedImage generate(String prompt) {
        long startedAt = System.nanoTime();
        Image image = callImageModel(prompt.strip());
        byte[] imageBytes = decodeImage(image);
        String filename = UUID.randomUUID() + ".png";
        Path target = storageDirectory.resolve(filename).normalize();

        // 文件名由服务端 UUID 生成；该检查继续保证未来修改文件名规则时不会发生路径穿越。
        if (!target.startsWith(storageDirectory)) {
            throw new ImageStorageException("生成的图片路径超出了配置的存储目录", new IllegalStateException("Invalid image target path"));
        }

        try {
            Files.createDirectories(storageDirectory);
            // CREATE_NEW 可以避免极端情况下同名文件被静默覆盖
            Files.write(target, imageBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ImageStorageException("无法将生成图片保存到本地目录", exception);
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info("Image generated and saved: filename={}, bytes={}, elapsedMs={}", filename, imageBytes.length, elapsedMillis);
        return new GeneratedImage(filename, imageBytes);
    }

    /**
     * 调用 LangChain4j，并把 SDK 或上游服务异常转换为稳定的业务异常
     */
    private Image callImageModel(String prompt) {
        try {
            Response<Image> response = imageModel.generate(prompt);
            Image image = response.content();
            if (image == null) {
                throw new ImageGenerationException("图片模型返回了空内容");
            }
            return image;
        } catch (ImageGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Image model request failed: type={}", exception.getClass().getSimpleName());
            throw new ImageGenerationException("图片模型调用失败", exception);
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
                    throw new ImageGenerationException("图片模型返回了空图片");
                }
                return bytes;
            } catch (IllegalArgumentException exception) {
                throw new ImageGenerationException("图片模型返回的 base64 数据无效", exception);
            }
        }

        URI imageUrl = image.url();
        if (imageUrl == null) {
            throw new ImageGenerationException("图片模型未返回图片数据或图片 URL");
        }
        return downloadImage(imageUrl);
    }

    /**
     * 下载模型返回的图片 URL，并限制为 HTTPS，避免请求不安全的本地或明文地址。
     */
    private byte[] downloadImage(URI imageUrl) {
        if (!"https".equalsIgnoreCase(imageUrl.getScheme())
                || imageUrl.getHost() == null
                || imageUrl.getRawUserInfo() != null) {
            throw new ImageGenerationException("图片模型返回了无效的图片 URL");
        }

        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(imageUrl)
                    .timeout(timeout)
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationException("下载模型生成的图片时被中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ImageGenerationException("下载模型生成的图片失败", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ImageGenerationException("下载模型生成的图片失败，HTTP 状态码：" + response.statusCode());
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ImageGenerationException("图片 URL 返回的内容不是图片");
        }

        byte[] imageBytes = response.body();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ImageGenerationException("图片 URL 返回了空图片");
        }
        return imageBytes;
    }
}
