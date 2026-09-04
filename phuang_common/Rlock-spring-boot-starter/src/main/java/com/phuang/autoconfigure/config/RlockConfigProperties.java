package com.phuang.autoconfigure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * huangpeng
 * 2023/8/14 23:52
 */
@Data
@ConfigurationProperties(prefix = RlockConfigProperties.PREFIX)
public class RlockConfigProperties {

    public static final String PREFIX = "redis.rlock";

    private int port = 6379;

    private int database = 0;

    private String password;

    /**
     * 锁等待时间
     */
    private long waitTime = -1;

    /**
     * 锁超时时间
     */
    private long leaseTime = -1;

    /**
     * 看门狗机制的超时时间(默认30s,仅仅当leaseTime = -1时生效)
     */
    private int lockWatchdogTimeout = 30000;

    /**
     * @See ServerTypeEnum
     */
    private int serverType = 1;

    private SingleServer singleServer;

    @Data
    public static class SingleServer {
        private String host = "localhost";
    }
}
