package com.phuang.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phuang.model.exception.BusinessException;
import com.phuang.model.dto.MinerUParseResult;
import com.phuang.service.impl.FileStorageService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 精准解析 API 工具类
 */
@Service
public class MineruParseUtil {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private static final long MAX_EXTRACTED_FILE_SIZE = 50L * 1024 * 1024;
    private static final Duration API_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ZIP_DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String apiToken;
    private final String modelVersion;
    private final String markdownObjectPrefix;

    public MineruParseUtil(FileStorageService fileStorageService,
                           ObjectMapper objectMapper,
                           @Value("${mineru.base-url:https://mineru.net/api/v4}") String apiBaseUrl,
                           @Value("${mineru.api-token:}") String apiToken,
                           @Value("${mineru.model-version:vlm}") String modelVersion,
                           @Value("${mineru.markdown-object-prefix:mineru}") String markdownObjectPrefix) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.apiToken = apiToken == null ? "" : apiToken.strip();
        this.modelVersion = modelVersion;
        this.markdownObjectPrefix = normalizeObjectPrefix(markdownObjectPrefix);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 使用 MinIO 文件访问地址创建 MinerU 精准解析任务。
     *
     * @param minioFileUrl MinerU 服务可访问的 MinIO 文件 URL
     * @return MinerU 任务 ID
     */
    public String createParseTask(String minioFileUrl) {
        URI fileUri = requireHttpUri(minioFileUrl, "MinIO 文件地址");

        // 精准解析接口不接收文件流，而是由 MinerU 主动访问这里提供的 MinIO URL。
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("url", fileUri.toString());
        requestBody.put("model_version", modelVersion);

        HttpRequest request = authenticatedRequestBuilder(URI.create(apiBaseUrl + "/extract/task"))
                .timeout(API_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(requestBody), StandardCharsets.UTF_8))
                .build();

        // sendJson 已统一校验 HTTP 状态码、业务 code 和 data 对象，此处只提取任务 ID。
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
        String normalizedTaskId = requireTaskId(taskId);
        HttpRequest request = authenticatedRequestBuilder(
                URI.create(apiBaseUrl + "/extract/task/" + normalizedTaskId))
                .timeout(API_REQUEST_TIMEOUT)
                .header("is_ocr", String.valueOf(true))
                .GET()
                .build();
        JsonNode data = sendJson(request);
        String state = requireText(data, "state");
        String errorMessage = optionalText(data, "err_msg");

        // 解析失败时保留 MinerU 返回的错误原因，交给上层决定是否重试或终止
        if ("failed".equalsIgnoreCase(state)) {
            return new MinerUParseResult(normalizedTaskId, state, null, errorMessage);
        }

        // pending、running、converting 都属于处理中状态，不提前下载结果文件
        if (!"done".equalsIgnoreCase(state)) {
            return new MinerUParseResult(normalizedTaskId, state, null, null);
        }

        // 只有 done 状态才会包含 full_zip_url，并触发 ZIP 下载及 Markdown 入库
        String fullZipUrl = requireText(data, "full_zip_url");
        String markdownMinioUrl = downloadMarkdownAndUpload(normalizedTaskId, fullZipUrl);
        return new MinerUParseResult(normalizedTaskId, state, markdownMinioUrl, null);
    }

    /**
     * 核心逻辑: 下载 MinerU 解析结果，并把 Markdown 及其图片上传到 MinIO
     * 执行顺序:
     *   1.下载 MinerU 返回的 ZIP 压缩包
     *   2.提取 full.md 文件和同级 images 目录文件
     *   3.先上传图片，获得每张图片对应的 MinIO 访问地址
     *   4.full.md 文件中的图片地址替换成minio图片地址(一并调用LLM获取图片描述添加至md文件)
     *   5.最后上传修改后的 markdown文件的 minio 地址
     *
     * @param taskId MinerU 解析任务 ID，同时作为 MinIO 对象目录的一部分
     * @param fullZipUrl MinerU 返回的完整结果 ZIP 下载地址
     * @return 修改并上传后的 Markdown 文件访问地址
     */
    private String downloadMarkdownAndUpload(String taskId, String fullZipUrl) {
        URI zipUri = requireHttpUri(fullZipUrl, "MinerU ZIP 地址");
        HttpRequest request = HttpRequest.newBuilder(zipUri)
                .timeout(ZIP_DOWNLOAD_TIMEOUT)
                .header("Accept", "application/zip, application/octet-stream")
                .GET()
                .build();
        Path temporaryDirectory = null;
        try {
            // 使用 InputStream 流式下载 ZIP，避免先将整个压缩包一次性读入内存。
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream responseBody = response.body()) {
                    responseBody.readNBytes(2048);
                }
                throw new BusinessException("下载 MinerU 解析结果失败，HTTP 状态码：" + response.statusCode());
            }

            // Markdown 和 images 都放在本次任务独占的临时目录中，处理完成后统一删除。
            temporaryDirectory = Files.createTempDirectory("mineru-" + taskId + "-");
            Path markdownFile = temporaryDirectory.resolve("full.md");
            Map<String, Path> extractedImages;
            try (InputStream responseBody = response.body();
                 ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(responseBody), StandardCharsets.UTF_8)) {
                // key 是 Markdown 中使用的相对路径（如 images/xxx.jpg），value 是本地临时图片路径
                extractedImages = extractMarkdownAndImages(zipInputStream, temporaryDirectory, markdownFile);
            }

            // 必须先上传图片，只有拿到真实的 MinIO 地址后才能修改 Markdown 内容
            Map<String, String> imageMinioUrls = uploadImages(taskId, extractedImages);
            replaceMarkdownImageUrls(markdownFile, imageMinioUrls);

            // 图片路径替换完成后再上传 Markdown，保证 MinIO 中保存的是最终可直接访问图片的版本
            String objectName = markdownObjectPrefix + "/" + taskId + "/full.md";
            try (InputStream markdownInputStream = Files.newInputStream(markdownFile)) {
                try {
                    return fileStorageService.uploadFile(
                            objectName,
                            markdownInputStream,
                            Files.size(markdownFile),
                            "text/markdown; charset=UTF-8"
                    );
                } catch (Exception e) {
                    throw new BusinessException("上传 MinerU Markdown 到 MinIO 失败", e);
                }
            }
        } catch (InterruptedException e) {
            // 恢复中断标记，避免上层线程池无法感知取消或停机信号
            Thread.currentThread().interrupt();
            throw new BusinessException("下载 MinerU 解析结果时线程被中断", e);
        } catch (IOException e) {
            throw new BusinessException("读取 MinerU 解析结果失败", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("处理 MinerU 解析结果失败", e);
        } finally {
            // 无论上传成功还是失败都清理本次任务产生的 Markdown、图片和临时目录。
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    /**
     * 从 ZIP 中提取 {@code full.md} 和任意层级下名为 {@code images} 的目录内容。
     * MinerU 可能会在 ZIP 外层增加任务目录，因此不能假定 {@code full.md} 一定位于 ZIP 根目录。
     * 图片保存时会统一转换为 {@code images/文件名} 形式，和 Markdown 中的相对地址保持一致。
     *
     * @param zipInputStream MinerU 结果 ZIP 输入流
     * @param temporaryDirectory 本次解析任务的临时目录
     * @param markdownFile {@code full.md} 的本地目标路径
     * @return Markdown 图片相对路径与本地临时文件路径的对应关系
     */
    private Map<String, Path> extractMarkdownAndImages(ZipInputStream zipInputStream,
                                                       Path temporaryDirectory,
                                                       Path markdownFile) throws IOException {
        Map<String, Path> extractedImages = new LinkedHashMap<>();
        boolean markdownFound = false;
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                zipInputStream.closeEntry();
                continue;
            }

            String normalizedEntryName = normalizeZipEntryName(entry.getName());
            if ("full.md".equalsIgnoreCase(baseName(normalizedEntryName))) {
                // 始终写入固定的安全路径，不直接使用 ZIP entry 路径，避免目录穿越问题。
                copyZipEntry(zipInputStream, markdownFile, "MinerU Markdown 文件");
                markdownFound = true;
            } else {
                String imageRelativePath = findImageRelativePath(normalizedEntryName);
                if (imageRelativePath != null) {
                    Path imageFile = temporaryDirectory.resolve(imageRelativePath).normalize();
                    // 即使 ZIP entry 名称异常，也不允许文件被写到临时目录之外。
                    if (!imageFile.startsWith(temporaryDirectory)) {
                        throw new BusinessException("MinerU ZIP 中包含不安全的图片路径：" + entry.getName());
                    }
                    Files.createDirectories(imageFile.getParent());
                    copyZipEntry(zipInputStream, imageFile, "MinerU 图片文件");
                    extractedImages.put(imageRelativePath, imageFile);
                }
            }
            zipInputStream.closeEntry();
        }

        if (!markdownFound) {
            throw new BusinessException("MinerU 结果 ZIP 中未找到 full.md 文件");
        }
        return extractedImages;
    }

    /**
     * 将已经定位到的 ZIP entry 写入本地临时文件。
     * 单个文件最多允许 50MB，避免异常压缩包解压后占满磁盘。
     */
    private void copyZipEntry(ZipInputStream zipInputStream, Path targetFile, String fileDescription) throws IOException {
        long totalBytes = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream outputStream = Files.newOutputStream(
                targetFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = zipInputStream.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > MAX_EXTRACTED_FILE_SIZE) {
                    throw new BusinessException(fileDescription + "超过 50MB 安全限制");
                }
                outputStream.write(buffer, 0, read);
            }
        }
    }

    /**
     * 逐张上传解压出的图片，并记录 Markdown 相对路径与 MinIO 地址的映射。
     * MinIO 对象仍保留 {@code images/...} 目录结构，便于定位和后续清理。
     */
    private Map<String, String> uploadImages(String taskId, Map<String, Path> extractedImages) {
        Map<String, String> imageMinioUrls = new LinkedHashMap<>();
        for (Map.Entry<String, Path> imageEntry : extractedImages.entrySet()) {
            String relativePath = imageEntry.getKey();
            Path imageFile = imageEntry.getValue();
            String objectName = markdownObjectPrefix + "/" + taskId + "/" + relativePath;
            try (InputStream imageInputStream = Files.newInputStream(imageFile)) {
                String imageUrl = fileStorageService.uploadFile(
                        objectName,
                        imageInputStream,
                        Files.size(imageFile),
                        detectImageContentType(relativePath)
                );
                imageMinioUrls.put(relativePath, imageUrl);
            } catch (Exception e) {
                throw new BusinessException("上传 MinerU 图片到 MinIO 失败:" + relativePath, e);
            }
        }
        return imageMinioUrls;
    }

    /**
     * 把 Markdown 中形如 {@code images/a.jpg} 或 {@code ./images/a.jpg} 的相对地址，
     * 替换为图片上传后返回的 MinIO 地址。
     *
     * <p>对于 {@code ![](images/a.jpg)} 这种描述为空的 Markdown 图片，会先调用视觉模型生成图片描述，
     * 最终写成 {@code ![图片描述](MinIO图片地址)}。其他普通路径仍直接替换，因此也兼容
     * HTML 图片标签 {@code <img src="images/a.jpg">}。</p>
     */
    private void replaceMarkdownImageUrls(Path markdownFile, Map<String, String> imageMinioUrls) throws IOException {
        String markdownContent = Files.readString(markdownFile, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> imageUrlEntry : imageMinioUrls.entrySet()) {
            String relativePath = imageUrlEntry.getKey();
            String imageUrl = imageUrlEntry.getValue();

            // 只匹配 alt 为空且地址是当前图片相对路径的 Markdown 图片语法。
            // 同时兼容 ![](images/a.jpg) 和 ![](./images/a.jpg) 两种写法。
            Pattern emptyDescriptionImagePattern = Pattern.compile(
                    "!\\[\\]\\((?:\\./)?" + Pattern.quote(relativePath) + "\\)"
            );
            Matcher emptyDescriptionImageMatcher = emptyDescriptionImagePattern.matcher(markdownContent);
            if (emptyDescriptionImageMatcher.find()) {
                // 图片已经上传到 MinIO，此处使用可访问的 MinIO 地址调用视觉模型生成描述。
                // 同一张图片在 Markdown 中出现多次时只调用一次模型，然后统一替换全部引用。
                String imageDescription = normalizeImageDescription(generateImageDescription(imageUrl));
                String markdownImage = "![" + imageDescription + "](" + imageUrl + ")";
                markdownContent = emptyDescriptionImageMatcher.replaceAll(Matcher.quoteReplacement(markdownImage));
            }

            // 前置边界用于避免误改已经是 http://.../images/a.jpg 的绝对地址。
            // optional "./" 让两种常见相对路径写法在一次替换中完成，避免二次替换污染新 URL。
            Pattern relativeImagePattern = Pattern.compile("(?<![A-Za-z0-9_:/.-])(?:\\./)?" + Pattern.quote(relativePath));
            markdownContent = relativeImagePattern.matcher(markdownContent).replaceAll(Matcher.quoteReplacement(imageUrl));
        }

        Files.writeString(
                markdownFile,
                markdownContent,
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    /**
     * 清理视觉模型返回的图片描述，使其可以安全地放入 Markdown 图片的方括号中。
     * 模型偶尔可能返回换行或方括号，这里将换行压缩为空格，并转义方括号，避免破坏 Markdown 语法。
     */
    private static String normalizeImageDescription(String imageDescription) {
        if (imageDescription == null || imageDescription.isBlank()) {
            throw new BusinessException("视觉模型未返回图片描述");
        }
        return imageDescription.strip()
                .replaceAll("[\\r\\n]+", " ")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    /**
     * 生成图片描述
     * 需要注意的是，如果你用的是外部的模型，这个url需要是公网可以访问的url。否则模型需要能和MinIO进行内网通信。
     */
    public String generateImageDescription(String imageUrl) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(chatModelApiKey)
                .baseUrl(chatModelBaseUrl)
                .modelName("qwen3-vl-plus")
                .temperature(0.7)
                .logResponses(true)
                .logRequests(true)
                .build();
        UserMessage userMessage = UserMessage.from(new TextContent("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明，不要增加任何特殊符号，特别是换行符"), new ImageContent(imageUrl));
        return chatModel.chat(userMessage)
                .aiMessage()
                .text();
    }

    /**
     * 从 ZIP entry 的完整路径中截取 {@code images/...} 部分。
     * 例如 {@code task-result/images/a.jpg} 会转换为 {@code images/a.jpg}。
     */
    private static String findImageRelativePath(String entryName) {
        if (entryName.startsWith("images/")) {
            return entryName.length() > "images/".length() ? entryName : null;
        }

        int imagesDirectoryIndex = entryName.indexOf("/images/");
        if (imagesDirectoryIndex < 0) {
            return null;
        }
        String relativePath = entryName.substring(imagesDirectoryIndex + 1);
        return relativePath.length() > "images/".length() ? relativePath : null;
    }

    /**
     * 统一 ZIP 内的路径分隔符，并拒绝包含上级目录跳转的 entry 名称。
     */
    private static String normalizeZipEntryName(String entryName) {
        String normalizedName = entryName.replace('\\', '/');
        while (normalizedName.startsWith("./")) {
            normalizedName = normalizedName.substring(2);
        }
        if (normalizedName.startsWith("/")
                || normalizedName.equals("..")
                || normalizedName.startsWith("../")
                || normalizedName.contains("/../")) {
            throw new BusinessException("MinerU ZIP 中包含不安全的文件路径：" + entryName);
        }
        return normalizedName;
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

    /**
     * 逆序删除临时目录内容：先删除文件和子目录，最后删除根目录。
     * 清理失败不覆盖主流程的成功结果或原始异常。
     */
    private static void deleteTemporaryDirectory(Path temporaryDirectory) {
        if (temporaryDirectory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时文件会由操作系统后续清理，不影响本次业务结果。
                }
            });
        } catch (IOException ignored) {
            // 目录遍历失败同样不应覆盖真正的解析或上传结果。
        }
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

    private static URI requireHttpUri(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        URI uri;
        try {
            uri = URI.create(value.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + "格式不正确", e);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(fieldName + "必须是有效的 HTTP/HTTPS 地址");
        }
        return uri;
    }

    private static String requireTaskId(String taskId) {
        String value = taskId == null ? "" : taskId.strip();
        if (!TASK_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("taskId 格式不正确");
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("mineru.base-url 不能为空");
        }
        return result;
    }

    private static String normalizeObjectPrefix(String value) {
        String result = value == null ? "" : value.strip().replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isBlank() || result.contains("..")) {
            throw new IllegalArgumentException("mineru.markdown-object-prefix 配置不正确");
        }
        return result;
    }

    private static String baseName(String zipEntryName) {
        String normalizedName = zipEntryName.replace('\\', '/');
        int separatorIndex = normalizedName.lastIndexOf('/');
        return separatorIndex >= 0 ? normalizedName.substring(separatorIndex + 1) : normalizedName;
    }

}
