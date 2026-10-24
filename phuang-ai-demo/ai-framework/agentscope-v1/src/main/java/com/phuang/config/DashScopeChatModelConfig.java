package com.phuang.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.EndpointType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "dash-scope.chat-model")
public class DashScopeChatModelConfig {

    private String apiKey;

    private String url;

    private String qianwenModelName;

    private String deepseekModelName;

    @Bean
    @ConditionalOnProperty(prefix = "dash-scope.chat-model", name = "qianwen-model-name")
    public DashScopeChatModel qianwenChatModel() {
        return buildChatModel(qianwenModelName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dash-scope.chat-model", name = "deepseek-model-name")
    public DashScopeChatModel deepseekChatModel() {
        return buildChatModel(deepseekModelName);
    }

    private DashScopeChatModel buildChatModel(String modelName) {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(url)
                .modelName(modelName)
                .endpointType(EndpointType.AUTO)
                .build();
    }
}
