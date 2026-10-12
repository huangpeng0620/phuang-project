package com.sprouttrail.image.config;

import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 图片模型配置
 *
 * <p>第一版直接创建一个 {@link ImageModel} Bean，避免引入聊天模型、AI Service、
 * Memory、Tool 等文生图流程不需要的能力。
 */
@Configuration
public class ImageModelConfig {

    /**
     * 创建负责文生图的 LangChain4j 模型
     *
     * <p>模型名称使用字符串配置而不是枚举，原因是图片模型可能先于 LangChain4j
     * 的枚举版本发布。这样可以通过环境变量升级模型，而无需修改和重新编译代码。
     *
     * @param properties 图片模型配置
     * @return 可注入业务服务的图片模型
     */
    @Bean
    public ImageModel imageModel(ImageProperties properties) {
        return OpenAiImageModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .size(properties.size())
                .quality(properties.quality())
                .outputFormat("png")
                .timeout(properties.timeout())
                .maxRetries(1)
                .build();
    }
}
