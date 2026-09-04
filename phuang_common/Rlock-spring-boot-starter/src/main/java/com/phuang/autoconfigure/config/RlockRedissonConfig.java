package com.phuang.autoconfigure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * huangpeng
 * 2023/8/14 23:47
 */
@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(RlockConfigProperties.class)
public class RlockRedissonConfig {

    @Resource
    private RlockConfigProperties rlockConfigProperties;

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        //设置全局看门狗机制锁续期超时时间
        config.setLockWatchdogTimeout(rlockConfigProperties.getLockWatchdogTimeout());
        //目前仅支持单机模式 TODO
        if (Objects.nonNull(rlockConfigProperties.getSingleServer())) {
            SingleServerConfig singleServerConfig = config.useSingleServer()
                    .setAddress("redis://" + rlockConfigProperties.getSingleServer().getHost() + ":" + rlockConfigProperties.getPort())
                    .setDatabase(rlockConfigProperties.getDatabase());
            if (Objects.nonNull(rlockConfigProperties.getPassword())) {
                singleServerConfig.setPassword(rlockConfigProperties.getPassword());
            }
        }
        return Redisson.create(config);
    }
}
