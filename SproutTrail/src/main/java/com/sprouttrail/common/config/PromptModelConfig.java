package com.sprouttrail.common.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提示词优化使用的文本模型配置。 */
@Configuration
public class PromptModelConfig {

    @Resource
    private ImageProperties properties;

    /**
     * 创建提示词优化模型，模型地址为空时复用图片模型地址。
     *
     * <p>保留原 Bean 方法名，确保现有按类型注入的提示词服务行为不变。
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
