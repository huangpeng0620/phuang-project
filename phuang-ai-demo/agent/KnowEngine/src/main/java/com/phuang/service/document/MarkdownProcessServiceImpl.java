package com.phuang.service.document;

import com.phuang.model.constant.ContentType;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.enums.DocumentStatus;
import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.FileProcessService;
import com.phuang.service.KnowledgeDocumentService;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  文件处理服务 - 负责 Markdown 文件转换处理
 *  该类的职责：
 *  1.直接读取 Markdown 内容
 *  2.解析其中的图片标签 ![alt](url)
 *  3.调用视觉大模型为每张图片生成描述，替换 alt 文本
 *  4.将处理后的 Markdown 上传到 MinIO，并更新文档状态为 CONVERTED
 */
@Slf4j
@Service
public class MarkdownProcessServiceImpl implements FileProcessService {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    private static final String CONVERTED_FILE_DIR = "converted/markdown/";

    /**
     * 匹配 Markdown 图片标签：![alt](url)
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\(([^)]+)\\)");

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    private OpenAiChatModel chatModel;

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

    @Override
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) {
        log.info("开始处理 Markdown 文档图片描述生成，documentId: {}", document.getDocTitle());

        //更新文档状态至【转换中】
        knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTING);

        try {
            //读取 Markdown 文件内容
            String mdContent = readInputStreamAsString(inputStream);

            // 为图片生成描述并替换 alt 文本
            String processedMdContent = enrichImageDescriptions(mdContent);

            // 上传处理后的 Markdown 到 MinIO
            String docTitle = document.getDocTitle();
            String baseName = docTitle.contains(".") ? docTitle.substring(0, docTitle.lastIndexOf(".")) : docTitle;
            String convertedObjectName = CONVERTED_FILE_DIR + baseName + ".md";
            String convertedUrl = fileStorageService.uploadFile(convertedObjectName, processedMdContent.getBytes(StandardCharsets.UTF_8), ContentType.TEXT_MARKDOWN);

            //更新文档状态至【已转换】
            knowledgeDocumentService.advanceDocumentAndVersionStatus(document.getDocId(), document.getCurrentVersionId(), DocumentStatus.CONVERTED);
            log.info("Markdown 文档处理完成，documentId: {}, convertedUrl: {}", document.getDocTitle(), convertedUrl);
            return convertedUrl;
        } catch (Exception e) {
            log.error("Markdown 文档处理失败，documentId: {}", document.getDocTitle(), e);
            // 保留版本当前状态，由上传补偿任务重试；避免失败时提前写入新的 currentVersionId。
            throw new BusinessException("Markdown 文档处理失败: " + e.getMessage(), e);
        } finally {
            closeQuietly(inputStream);
        }
    }

    /**
     * 遍历 Markdown 中的图片标签，为每张图片调用视觉模型生成描述并替换 alt 文本
     * 例如：![image](https://xxx/foo.jpg) -> ![这是一张xxx图片，xxx描述](https://xxx/foo.jpg)
     */
    private String enrichImageDescriptions(String mdContent) {
        Matcher matcher = IMAGE_PATTERN.matcher(mdContent);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String originAlt = matcher.group(1);
            String imageUrl = matcher.group(2);

            String description;
            try {
                // 调用基类提供的视觉模型生成描述
                description = generateImageDescription(imageUrl);
                if (description == null || description.isBlank()) {
                    log.warn("图片描述为空，保留原 alt 文本，url: {}", imageUrl);
                    description = originAlt;
                } else {
                    // 去除可能存在的换行，保证 Markdown 标签单行
                    description = description.replaceAll("[\\r\\n]+", " ").trim();
                }
                log.info("图片描述已生成: {} -> {}", imageUrl, description);
            } catch (Exception e) {
                log.warn("生成图片描述失败，保留原 alt 文本，url: {}", imageUrl, e);
                description = originAlt;
            }

            String newImageTag = "![" + description + "](" + imageUrl + ")";
            matcher.appendReplacement(result, Matcher.quoteReplacement(newImageTag));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 生成图片描述
     * 需要注意的是，如果你用的是外部的模型，这个url需要是公网可以访问的url。否则模型需要能和MinIO进行内网通信。
     */
    public String generateImageDescription(String imageUrl) {
        UserMessage userMessage = UserMessage.from(new TextContent("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明，不要增加任何特殊符号，特别是换行符"), new ImageContent(imageUrl));
        return chatModel.chat(userMessage)
                .aiMessage()
                .text();
    }

    /**
     * 将 InputStream 全量读取为字符串
     */
    private String readInputStreamAsString(InputStream inputStream) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 安静关闭输入流，忽略异常
     */
    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.MARKDOWN;
    }
}
