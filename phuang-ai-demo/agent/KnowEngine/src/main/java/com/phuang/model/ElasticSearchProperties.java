package com.phuang.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = ElasticSearchProperties.PREFIX)
public class ElasticSearchProperties {

    public static final String PREFIX = "elasticsearch";

    private String host;

    private String baseUrl;

    private String modelName;

    private String apiKey;

    private int dimensions;
}
