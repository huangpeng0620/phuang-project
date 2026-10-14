package com.sprouttrail.image.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建 MinIO 客户端，供 MinioService 调用
 */
@Configuration
public class MinioConfig {

    /**
     * 根据 application.yml 中的 MinIO 地址和访问凭证创建客户端
     *
     * @param url MinIO 服务端地址
     * @param accessKey MinIO 访问账号
     * @param secretKey MinIO 访问密钥
     * @return 可注入到 MinioService 的 MinIO 客户端
     */
    @Bean
    public MinioClient minioClient(
            @Value("${minio.url}") String url,
            @Value("${minio.accessKey}") String accessKey,
            @Value("${minio.secretKey}") String secretKey) {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
