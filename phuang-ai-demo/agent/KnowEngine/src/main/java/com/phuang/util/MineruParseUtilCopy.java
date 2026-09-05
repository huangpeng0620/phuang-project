package com.phuang.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.phuang.model.constant.ContentType;
import com.phuang.model.dto.MinerUParseResult;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.impl.FileStorageService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 精准解析 API 工具类
 */
@Slf4j
@Service
public class MineruParseUtilCopy {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    @Value("${mineru.base-url:https://mineru.net/api/v4}")
    private String apiBaseUrl;

    @Value("${mineru.api-token:}")
    private String apiToken;

    @Value("${mineru.model-version:vlm}")
    private String modelVersion;

    @Resource
    private FileStorageService fileStorageService;

    private OpenAiChatModel chatModel;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private static final Duration API_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration ZIP_DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    public MineruParseUtilCopy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PostConstruct
    public void init() {
        chatModel = OpenAiChatModel.builder()
                .apiKey(chatModelApiKey)
                .baseUrl(chatModelBaseUrl)
                .modelName("qwen3-vl-plus")
                .temperature(0.7)
                .logResponses(true)
                .logRequests(true)
                .build();
    }

    /**
     * 使用 MinIO 文件访问地址创建 MinerU 精准解析任务。
     *
     * @param minioFileUrl MinerU 服务可访问的 MinIO 文件 URL
     * @return MinerU 任务 ID
     */
    public String createParseTask(String minioFileUrl) {
        // 精准解析接口不接收文件流，而是由 MinerU 主动访问这里提供的 MinIO URL
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("url", minioFileUrl);
        requestBody.put("model_version", modelVersion);
        HttpRequest request = authenticatedRequestBuilder(URI.create(apiBaseUrl + "/extract/task"))
                .timeout(API_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(requestBody), StandardCharsets.UTF_8))
                .build();

        // sendJson 已统一校验 HTTP 状态码、业务 code 和 data 对象，此处只提取任务 ID
        JsonNode data = sendJson(request);
        return requireText(data, "task_id");
    }

    /**
     * 查询 MinerU 解析任务。任务完成时下载结果 ZIP，提取其中的 Markdown 并上传到 MinIO。
     * 重复查询已完成任务会覆盖同一 taskId 对应的 MinIO 对象，便于调用方安全重试。
     *
     * @param taskId MinerU 任务 ID
     * @return 解析状态及 Markdown 的 MinIO 地址
     */
    public MinerUParseResult queryParseResult(String taskId) {
        HttpRequest request = authenticatedRequestBuilder(
                URI.create(apiBaseUrl + "/extract/task/" + taskId))
                .timeout(API_REQUEST_TIMEOUT)
                .header("is_ocr", String.valueOf(true))
                .GET()
                .build();
        JsonNode data = sendJson(request);
        String state = requireText(data, "state");
        String errorMessage = optionalText(data, "err_msg");

        // 解析失败时保留 MinerU 返回的错误原因，交给上层决定是否重试或终止
        if ("failed".equalsIgnoreCase(state)) {
            return new MinerUParseResult(taskId, state, null, errorMessage);
        }

        // pending、running、converting 都属于处理中状态，不提前下载结果文件
        if (!"done".equalsIgnoreCase(state)) {
            return new MinerUParseResult(taskId, state, null, null);
        }

        // 只有 done 状态才会包含 full_zip_url，并触发 ZIP 下载及 Markdown 入库
        String fullZipUrl = requireText(data, "full_zip_url");
        String markdownMinioUrl = downloadMarkdownAndUpload(taskId, fullZipUrl);
        return new MinerUParseResult(taskId, state, markdownMinioUrl, null);
    }

    private String downloadMarkdownAndUpload(String taskId, String fullZipUrl) {
        String zipFilePath = null;
        String extractDir = null;
        try {
            // 先创建本次任务使用的临时 ZIP 文件和解压目录。
            String tempDir = System.getProperty("java.io.tmpdir");
            String uniqueId = UUID.randomUUID().toString();
            zipFilePath = tempDir + File.separator + uniqueId + ".zip";
            extractDir = tempDir + File.separator + uniqueId + "_extracted";

            // HTTP 响应直接写入临时 ZIP 文件，避免将整个压缩包加载到 JVM 堆内存
            downloadZipToFile(fullZipUrl, Paths.get(zipFilePath));
            log.info("ZIP 文件已保存到本地临时目录下: {}", zipFilePath);

            // 解压 ZIP 文件
            extractZip(zipFilePath, extractDir);

            //md 文件处理并上传至 minio
            return processExtractedFiles(extractDir, taskId);
        } catch (Exception e) {
            throw new BusinessException("文档 ZIP 转换失败: " + e.getMessage(), e);
        } finally {
            // 异步清理临时文件
            cleanupTempFilesAsync(zipFilePath, extractDir);
        }
    }

    private String processExtractedFiles(String extractDir, String taskId) throws Exception {
        Path extractPath = Paths.get(extractDir);

        // 按照名称精确匹配 MinerU 的结果包中的 full.md 文件
        Path mdFile;
        try (Stream<Path> paths = Files.walk(extractPath)) {
            mdFile = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> "full.md".equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("解压后的文件夹中未找到 full.md 文件"));
        }

        // 处理 full.md 同级 images 目录中的图片
        Path imagesDirectory = mdFile.getParent().resolve("images");
        List<Path> imageFiles = new ArrayList<>();
        if (Files.isDirectory(imagesDirectory)) {
            try (Stream<Path> paths = Files.walk(imagesDirectory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return fileName.endsWith(".png") || fileName.endsWith(".jpg")
                                    || fileName.endsWith(".jpeg") || fileName.endsWith(".gif")
                                    || fileName.endsWith(".webp") || fileName.endsWith(".bmp");
                        })
                        .forEach(imageFiles::add);
            }
        }

        log.info("找到 Markdown 文件:{}, 图片文件数量:{}", mdFile, imageFiles.size());
        Map<String, String> imageUrlMap = Maps.newHashMap();
        String baseObjectName = String.format("converted/pdf/%s/", taskId);

        for (Path imagePath : imageFiles) {
            //获取图片相对于full.md 的完整相对路径,例如: images/chapter1/a.png
            String relativeImagePath = mdFile.getParent().relativize(imagePath).toString().replace(File.separatorChar, '/');

            // 使用文件输入流上传图片,避免将整张图片读取到 JVM 堆内存中
            String minioImageName = baseObjectName + relativeImagePath;
            try (InputStream imageInputStream = Files.newInputStream(imagePath)) {
                String imageUrl = fileStorageService.uploadFile(minioImageName, imageInputStream, Files.size(imagePath), detectImageContentType(relativeImagePath));
                imageUrlMap.put(relativeImagePath, imageUrl);
                log.info("图片已上传到 MinIO: {} -> {}", relativeImagePath, imageUrl);
            }
        }

        // 读取 md 文件内容
        String mdContent = Files.readString(mdFile, StandardCharsets.UTF_8);

        // 替换 md 中的图片地址为 MinIO 地址，并生成图片描述
        String processedMdContent = processMarkdownImages(mdContent, imageUrlMap);

        // 上传处理后的 md 文件到 MinIO
        String mdObjectName = baseObjectName + mdFile.getFileName().toString();
        String mdUrl = fileStorageService.uploadFile(mdObjectName, processedMdContent.getBytes(StandardCharsets.UTF_8), ContentType.TEXT_MARKDOWN);
        log.info("Markdown 文件已上传到 MinIO: {}", mdUrl);
        return mdUrl;
    }

    /**
     * 处理 Markdown 中的图片标签：替换地址并生成图片描述
     * @param mdContent md 文件内容
     * @param imageUrlMap image图片映射
     * @return
     */
    private String processMarkdownImages(String mdContent, Map<String, String> imageUrlMap) {
        // 匹配图片标签的正则表达式: ![alt](path)
        Pattern pattern = Pattern.compile("!\\[(.*?)\\]\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(mdContent);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String imagePath = matcher.group(2);
            // 统一路径分隔符并去掉开头的 ./，以便和 images/... 完整相对路径准确匹配。
            String normalizedImagePath = imagePath.replace('\\', '/');
            while (normalizedImagePath.startsWith("./")) {
                normalizedImagePath = normalizedImagePath.substring(2);
            }

            // 获取 MinIO 上的图片 URL
            String minioUrl = imageUrlMap.get(normalizedImagePath);
            if (minioUrl == null) {
                // 如果找不到对应的 MinIO URL，保持原样
                log.warn("未找到图片 {} 对应的 MinIO URL", normalizedImagePath);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            // 调用 LLM 生成图片描述
            String imageDescription = generateImageDescription(minioUrl);

            // 构建新的图片标签: ![描述](minio_url)
            String newImageTag = String.format("![%s](%s)", imageDescription, minioUrl);
            matcher.appendReplacement(result, Matcher.quoteReplacement(newImageTag));
            log.info("图片标签已处理: {} -> {}", imagePath, minioUrl);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 将 MinerU ZIP 地址的响应内容直接写入指定临时文件
     *
     * @param zipUri ZIP 文件下载地址
     * @param targetZipFile ZIP 文件本地保存路径
     */
    private void downloadZipToFile(String zipUri, Path targetZipFile) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(zipUri))
                .timeout(ZIP_DOWNLOAD_TIMEOUT)
                .header("Accept", "application/zip, application/octet-stream")
                .GET()
                .build();

        try {
            // HttpClient 在接收网络数据时直接写入文件，内存中只保留少量传输缓冲数据。
            HttpResponse<Path> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofFile(
                            targetZipFile,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)
            );

            // 只有 2xx 响应才是有效的 ZIP 下载结果，避免把 MinerU 错误响应当作压缩包处理。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("下载 MinerU ZIP 文件失败，HTTP 状态码：" + response.statusCode());
            }
            if (!Files.exists(response.body()) || Files.size(response.body()) == 0) {
                throw new BusinessException("下载 MinerU ZIP 文件失败：响应内容为空");
            }
        } catch (InterruptedException e) {
            // 恢复中断状态，使上层线程池仍能感知任务取消或应用停机信号。
            Thread.currentThread().interrupt();
            throw new BusinessException("下载 MinerU ZIP 文件时线程被中断", e);
        } catch (IOException e) {
            throw new BusinessException("下载 MinerU ZIP 文件失败", e);
        }
    }

    /**
     * 解压 ZIP 文件到指定目录
     */
    private void extractZip(String zipFilePath, String extractDir) throws IOException {
        Path extractPath = Paths.get(extractDir);
        Files.createDirectories(extractPath);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractPath.resolve(entry.getName());
                // 安全检查：防止 ZIP 路径遍历攻击
                if (!entryPath.normalize().startsWith(extractPath.normalize())) {
                    log.warn("跳过不安全的 ZIP 条目: {}", entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 调用LLM生成图片描述
     * @param imageUrl 图片url(公网可访问)
     * @return
     */
    public String generateImageDescription(String imageUrl) {
        UserMessage userMessage = UserMessage.from(new TextContent("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明，不要增加任何特殊符号，特别是换行符"), new ImageContent(imageUrl));
        return chatModel.chat(userMessage)
                .aiMessage()
                .text();
    }

    /**
     * 根据扩展名设置常见图片类型。无法识别时使用二进制类型，不影响文件上传和访问。
     */
    private static String detectImageContentType(String relativePath) {
        String lowerCasePath = relativePath.toLowerCase(Locale.ROOT);
        if (lowerCasePath.endsWith(".jpg") || lowerCasePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCasePath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCasePath.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerCasePath.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerCasePath.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // MinerU 同时存在 HTTP 状态码和响应体 code 两层成功标识，两者都必须通过。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("MinerU API 请求失败，HTTP 状态码：" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.has("code") || root.path("code").asInt(Integer.MIN_VALUE) != 0) {
                throw new BusinessException("MinerU API 返回失败：" + optionalText(root, "msg"));
            }

            JsonNode data = root.path("data");
            if (!data.isObject()) {
                throw new BusinessException("MinerU API 响应缺少 data 对象");
            }
            return data;
        } catch (InterruptedException e) {
            // 与 ZIP 下载逻辑一致，包装异常前保留线程中断语义。
            Thread.currentThread().interrupt();
            throw new BusinessException("调用 MinerU API 时线程被中断", e);
        } catch (IOException e) {
            throw new BusinessException("调用 MinerU API 失败", e);
        }
    }

    private HttpRequest.Builder authenticatedRequestBuilder(URI uri) {
        if (apiToken.isBlank()) {
            throw new BusinessException("未配置 mineru.api-token（可通过 MINERU_API_TOKEN 环境变量提供）");
        }
        return HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiToken);
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (IOException e) {
            throw new BusinessException("构造 MinerU 请求参数失败", e);
        }
    }

    private static String requireText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException("MinerU API 响应缺少字段：" + fieldName);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * 异步清理临时文件
     */
    private void cleanupTempFilesAsync(String zipFilePath, String extractDir) {
        if (zipFilePath == null && extractDir == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                // 删除 ZIP 文件
                if (zipFilePath != null) {
                    Files.deleteIfExists(Paths.get(zipFilePath));
                    log.info("临时 ZIP 文件已删除: {}", zipFilePath);
                }

                // 删除解压目录
                if (extractDir != null) {
                    deleteDirectory(Paths.get(extractDir));
                    log.info("临时解压目录已删除: {}", extractDir);
                }
            } catch (Exception e) {
                log.warn("清理临时文件失败", e);
            }
        });
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((a, b) -> -a.compareTo(b)) // 反向排序，先删除子文件/目录
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path, e);
                        }
                    });
        }
    }

}
