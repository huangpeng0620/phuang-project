package com.phuang.model.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = Neo4jProperties.PREFIX)
public class Neo4jProperties {

    public static final String PREFIX = "neo4j";

    private String uri;

    private String username;

    private String password;
}
