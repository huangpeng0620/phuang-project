package com.sprouttrail.image;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SproutTrail 应用入口
 *
 * <p>{@link ConfigurationPropertiesScan} 会自动发现本项目中的配置属性类，
 * 让图片模型和超时时间都可以通过 application.yml 或环境变量配置。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SproutTrailApplication {

    /**
     * 启动 Spring Boot 应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SproutTrailApplication.class, args);
    }
}
