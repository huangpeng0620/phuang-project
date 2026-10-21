package com.phuang.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description SAA配置类
 * @author huangpeng
 */
@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String model;

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean(name = "deepSeekR1ChatModel")
    public ChatModel deepSeekR1ChatModel(@Qualifier("dashScopeApi") DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(model).build())
                .build();
    }

    @Bean(name = "deepSeekR1ChatClient")
    public ChatClient deepSeekR1ChatClient(@Qualifier("deepSeekR1ChatModel") ChatModel deepSeekR1ChatModel) {
        return ChatClient.builder(deepSeekR1ChatModel)
                .defaultOptions(ChatOptions.builder().model(model).build())
                .build();
    }

}
