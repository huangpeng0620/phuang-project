package com.drstrong.health.arthas.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;
import java.util.Set;

@ConfigurationProperties(prefix = AuthExtProperties.PREFIX)
@Configuration
@Data
public class AuthExtProperties implements Serializable {

    public static final String PREFIX = "arthas.tunnel";

    private String superAdminRoleSign;

    private Set<SecurityProperties.User> users;
}
