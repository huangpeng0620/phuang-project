package com.drstrong.health.arthas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * @author huangpeng
 * @description ArthasTunnelServerApplication
 * @since 2023/11/6
 */
@SpringBootApplication
@EnableWebFlux
@EnableDiscoveryClient
public class ArthasTunnelServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArthasTunnelServerApplication.class, args);
    }
}
