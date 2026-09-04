package com.drstrong.health.arthas.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"com.alibaba.arthas.tunnel.server.app.configuration"})
@EnableCaching
public class TunnelServerAuthConfiguration {

}
