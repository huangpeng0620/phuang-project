package com.sprouttrail.image.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprouttrail.image.model.enums.ImageAspectRatio;
import com.sprouttrail.image.model.adapter.QwenImageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 图片模型配置
 *
 * <p>提示词优化复用现有 API Key，并单独配置文本模型名称和兼容接口地址，
 * 避免影响已有图片模型的服务地址与模型配置。
 */
@Configuration
public class ImageModelConfig {

    @Resource
    private ImageProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    private final Map<ImageAspectRatio, ImageModel> imageModels = new ConcurrentHashMap<>();

    /**
     * 获取与指定画幅对应的 LangChain4j 图片模型。
     *
     * <p>LangChain4j 的图片尺寸配置属于模型实例级参数，因此按照画幅缓存模型，
     * 避免每次请求都重新创建 HTTP 客户端。
     *
     * @param aspectRatio 用户选择的图片画幅
     * @return 使用对应图片尺寸的模型实例
     */
    public ImageModel imageModel(ImageAspectRatio aspectRatio) {
        return imageModels.computeIfAbsent(aspectRatio, this::createImageModel);
    }

    private ImageModel createImageModel(ImageAspectRatio aspectRatio) {
        ImageModel generationModel = OpenAiImageModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getImageModel())
                .size(aspectRatio.imageSize())
                .timeout(properties.getTimeout())
                .maxRetries(1)
                .build();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new QwenImageModel(generationModel, properties, aspectRatio, objectMapper, httpClient);
    }

    /**
     * 创建只用于提示词优化的文本模型
     *
     * <p>文本模型名称和接口地址均从 {@link ImageProperties} 读取
     * 接口地址为空时复用图片模型配置，API Key 始终复用现有值
     *
     * @return LangChain4j 聊天模型
     */
    @Bean
    public ChatModel promptOptimizationModel() {
        String baseUrl = properties.getPromptBaseUrl().isBlank()
                ? properties.getBaseUrl()
                : properties.getPromptBaseUrl();
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(baseUrl)
                .modelName(properties.getPromptModel())
                .timeout(properties.getTimeout())
                .maxRetries(1)
                .build();
    }
}
