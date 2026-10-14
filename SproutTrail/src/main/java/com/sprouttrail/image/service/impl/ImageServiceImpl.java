package com.sprouttrail.image.service.impl;

import com.sprouttrail.image.config.ImageModelConfig;
import com.sprouttrail.image.config.ImageProperties;
import com.sprouttrail.image.exception.ImageWorkflowException;
import com.sprouttrail.image.mapper.ImageGenerationRecordMapper;
import com.sprouttrail.image.model.GeneratedImage;
import com.sprouttrail.image.model.ImageGenerationRecord;
import com.sprouttrail.image.model.ImageAspectRatio;
import com.sprouttrail.image.service.ImageService;
import com.sprouttrail.image.service.MinioService;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 完成图片生成、图片数据读取和 MinIO 上传
 *
 * <p>该服务不记录用户的完整 prompt，也不记录模型返回的 base64 内容，
 * 防止日志意外保存用户数据或产生超大日志文件。
 */
@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    /** 与 multipart 配置一致；服务层再次校验，防止绕过 HTTP 接口的调用。 */
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    @Resource
    private ImageModelConfig imageModelConfig;

    @Resource
    private ImageProperties properties;

    @Resource
    private MinioService minioService;

    @Resource
    private ImageGenerationRecordMapper imageGenerationRecordMapper;

    private HttpClient httpClient;

    /**
     * 在依赖注入完成后初始化用于下载模型图片的 HTTP 客户端。
     */
    @PostConstruct
    private void initialize() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 根据文字描述生成一张 PNG 图片并上传到 MinIO
     *
     * @param originalPrompt 用户输入的原始图片描述
     * @param optimizedPrompt 实际提交给图片模型的完整提示词
     * @param aspectRatio 用户选择的图片画幅
     * @return 图片文件名及其二进制内容
     * @throws ImageWorkflowException 模型调用或 MinIO 上传失败
     */
    @Override
    public GeneratedImage generate(String originalPrompt, String optimizedPrompt,
                                   ImageAspectRatio aspectRatio) {
        long startedAt = System.nanoTime();
        // 保留调用方传入的全部字符；提示词的前后空白也可能是用户有意输入的内容。
        Image image = callImageModel(optimizedPrompt, aspectRatio);
        GeneratedImage generatedImage = saveImage(image, startedAt);
        ImageGenerationRecord record = ImageGenerationRecord.builder()
                .originalPrompt(originalPrompt)
                .optimizedPrompt(optimizedPrompt)
                .minioImageUrl(generatedImage.getMinioUrl())
                .aspectRatio(aspectRatio.name())
                .build();
        imageGenerationRecordMapper.insert(record);
        return generatedImage;
    }

    /**
     * 基于上传的原图进行编辑，并复用文生图的结果下载和 MinIO 上传流程。
     *
     * @param upload 本次上传的单张原图
     * @param prompt 用户确认的编辑要求
     * @param aspectRatio 输出画幅
     * @return MinIO 对象名与 PNG 内容
     */
    @Override
    public GeneratedImage edit(MultipartFile upload, String prompt, ImageAspectRatio aspectRatio) {
        Image originalImage = readUploadedImage(upload);
        long startedAt = System.nanoTime();
        try {
            Response<Image> response = imageModelConfig.imageModel(aspectRatio).edit(originalImage, prompt);
            if (response == null || response.content() == null) {
                throw new IllegalStateException("Image editing returned no image");
            }
            return saveImage(response.content(), startedAt);
        } catch (ImageWorkflowException exception) {
            // 下载结果失败属于图片编辑失败，存储和模型返回的明确错误则保持原错误码。
            if (!"IMAGE_GENERATION_FAILED".equals(exception.code())) {
                throw exception;
            }
            throw imageEditFailure(exception);
        } catch (RuntimeException exception) {
            throw imageEditFailure(exception);
        }
    }

    /**
     * 检查图片真实格式、尺寸及可解码性，再使用原始字节构造模型输入。
     *
     * <p>先检查尺寸再解码，限制为 1600 万像素，避免小文件声明巨大尺寸造成内存耗尽。
     * 文件名与客户端声明的 Content-Type 不作为实际格式的判断依据。
     */
    static Image readUploadedImage(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw invalidUpload("请上传一张原图");
        }
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new ImageWorkflowException("IMAGE_UPLOAD_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE,
                    "原图不能超过 5MB");
        }

        try {
            byte[] bytes = upload.getBytes();
            try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw invalidUpload("请上传有效的 JPG 或 PNG 图片");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!"jpeg".equals(format) && !"png".equals(format)) {
                        throw invalidUpload("仅支持 JPG 和 PNG 图片");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 8000 || height > 8000
                            || (long) width * height > 16_000_000) {
                        throw invalidUpload("图片宽高需在 1～8000 像素之间，总像素不能超过 1600 万");
                    }
                    if (Math.max(width, height) > 10L * Math.min(width, height)) {
                        throw invalidUpload("原图长边不能超过短边的 10 倍");
                    }
                    if (reader.read(0) == null) {
                        throw invalidUpload("图片无法读取，请重新上传");
                    }
                    return Image.builder()
                            .base64Data(Base64.getEncoder().encodeToString(bytes))
                            .mimeType("png".equals(format) ? "image/png" : "image/jpeg")
                            .build();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new ImageWorkflowException("INVALID_IMAGE_UPLOAD", HttpStatus.BAD_REQUEST,
                    "图片无法读取，请上传有效的 JPG 或 PNG 图片", exception);
        }
    }

    /** 文生图与图片编辑共用结果读取、UUID 命名和 MinIO 上传规则。 */
    private GeneratedImage saveImage(Image image, long startedAt) {
        byte[] imageBytes = decodeImage(image);
        String filename = UUID.randomUUID() + ".png";
        String minioUrl;
        try {
            minioUrl = minioService.uploadFile(filename, imageBytes, MediaType.IMAGE_PNG_VALUE);
        } catch (Exception exception) {
            throw new ImageWorkflowException("IMAGE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片已生成，但上传到 MinIO 失败", exception);
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info("Image generated and uploaded to MinIO: filename={}, bytes={}, elapsedMs={}",
                filename, imageBytes.length, elapsedMillis);
        return new GeneratedImage(filename, imageBytes, minioUrl);
    }

    private static ImageWorkflowException invalidUpload(String message) {
        return new ImageWorkflowException("INVALID_IMAGE_UPLOAD", HttpStatus.BAD_REQUEST, message);
    }

    private ImageWorkflowException imageEditFailure(Throwable cause) {
        return new ImageWorkflowException("IMAGE_EDIT_FAILED", HttpStatus.BAD_GATEWAY,
                "图片优化失败，请稍后重试", cause);
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
