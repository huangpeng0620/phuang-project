package com.sprouttrail.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 提供项目内统一的 BCrypt 密码校验器。 */
@Configuration
public class PasswordEncoderConfig {

    /** 创建 BCrypt 编码器；登录时使用 matches 校验数据库中的密码哈希。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
