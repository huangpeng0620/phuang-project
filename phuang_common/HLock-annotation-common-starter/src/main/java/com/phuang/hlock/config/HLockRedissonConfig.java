package com.phuang.hlock.config;

import com.phuang.hlock.aspect.HLockAnnotationAspect;
import com.phuang.hlock.handler.LockInfoHandler;
import com.phuang.hlock.model.LockFactory;
import com.phuang.hlock.model.ServerTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HLock 的 Redisson 自动配置
 *
 * <p>该配置类只负责创建组件默认的 RedissonClient。业务项目已经声明
 * RedissonClient Bean 时，{@link ConditionalOnMissingBean} 会使本配置退让，
 * 从而复用业务项目自己的 Redis 连接和拓扑配置。</p>
 */
@Configuration
@Slf4j
@Import({HLockAnnotationAspect.class, LockInfoHandler.class, LockFactory.class})
@EnableConfigurationProperties(HLockConfigProperties.class)
public class HLockRedissonConfig {

    private static final String REDIS_PROTOCOL = "redis://";
    private static final String REDIS_SSL_PROTOCOL = "rediss://";

    private final HLockConfigProperties properties;

    /**
     * 使用构造器注入，确保创建 RedissonClient 前配置属性已经完成绑定。
     */
    public HLockRedissonConfig(HLockConfigProperties properties) {
        this.properties = properties;
    }

    /**
     * 按 server-type 创建 RedissonClient。
     *
     * <p>destroyMethod 指定为 shutdown，确保 Spring 容器关闭时释放 Netty
     * 线程、Redis 连接和 Redisson 内部资源。</p>
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        validateCommonProperties();

        Config config = new Config();
        // leaseTime=-1 时，Redisson 根据该值判断锁续期和失效时间。
        config.setLockWatchdogTimeout(properties.getLockWatchdogTimeout());

        ServerTypeEnum serverType = properties.getServerType();
        log.info("开始初始化 HLock RedissonClient, serverType={}", serverType);
        switch (serverType) {
            case SINGLE_SERVER:
                configureSingleServer(config);
                break;
            case CLUSTER_SERVERS:
                configureClusterServers(config);
                break;
            case MASTER_SLAVE_SERVERS:
                configureMasterSlaveServers(config);
                break;
            default:
                // 防御性分支，防止后续新增枚举后忘记同步实现构建逻辑。
                throw new IllegalStateException("Unsupported Redis server type: " + serverType);
        }

        RedissonClient redissonClient = Redisson.create(config);
        log.info("HLock RedissonClient 初始化完成, serverType={}", serverType);
        return redissonClient;
    }

    /**
     * 构建单机模式配置
     */
    private void configureSingleServer(Config config) {
        HLockConfigProperties.SingleServer single = properties.getSingleServer();
        require(single != null, "redis.hlock.single-server must be configured");
        require(StringUtils.hasText(single.getHost()),
                "redis.hlock.single-server.host must not be blank");
        validatePort(single.getPort(), "redis.hlock.single-server.port");
        require(single.getDatabase() >= 0,
                "redis.hlock.single-server.database must be >= 0");

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(singleServerAddress(single.getHost(), single.getPort()))
                .setDatabase(single.getDatabase());
        applyConnectionConfig(serverConfig);

        if (StringUtils.hasText(single.getUsername())) {
            serverConfig.setUsername(single.getUsername());
        }
        if (StringUtils.hasText(single.getPassword())) {
            serverConfig.setPassword(single.getPassword());
        }
    }

    /**
     * 构建 Redis Cluster 配置
     *
     * <p>Redis Cluster 只支持 database 0，因此 Cluster 配置不暴露 database。
     * Redisson 会使用配置的一个或多个种子节点发现完整集群拓扑。</p>
     */
    private void configureClusterServers(Config config) {
        HLockConfigProperties.ClusterServers cluster = properties.getClusterServers();
        require(cluster != null, "redis.hlock.cluster-servers must be configured");
        validateAddresses(cluster.getNodeAddresses(),
                "redis.hlock.cluster-servers.node-addresses");
        require(cluster.getScanInterval() > 0,
                "redis.hlock.cluster-servers.scan-interval must be > 0");

        ClusterServersConfig serverConfig = config.useClusterServers()
                .addNodeAddress(normalizeAddresses(cluster.getNodeAddresses()))
                .setScanInterval(cluster.getScanInterval());
        applyConnectionConfig(serverConfig);

        if (StringUtils.hasText(cluster.getUsername())) {
            serverConfig.setUsername(cluster.getUsername());
        }
        if (StringUtils.hasText(cluster.getPassword())) {
            serverConfig.setPassword(cluster.getPassword());
        }
    }

    /**
     * 构建固定主从模式配置
     *
     * <p>该模式要求明确指定一个主节点和至少一个从节点。它适用于拓扑固定的
     * Redis 主从部署；如果需要通过 Sentinel 自动发现主节点，应新增 Sentinel 模式。</p>
     */
    private void configureMasterSlaveServers(Config config) {
        HLockConfigProperties.MasterSlaveServers masterSlave = properties.getMasterSlaveServers();
        require(masterSlave != null,
                "redis.hlock.master-slave-servers must be configured");
        require(StringUtils.hasText(masterSlave.getMasterAddress()),
                "redis.hlock.master-slave-servers.master-address must not be blank");
        validateAddress(masterSlave.getMasterAddress(),
                "redis.hlock.master-slave-servers.master-address");
        validateAddresses(masterSlave.getSlaveAddresses(),
                "redis.hlock.master-slave-servers.slave-addresses");
        require(masterSlave.getDatabase() >= 0,
                "redis.hlock.master-slave-servers.database must be >= 0");

        MasterSlaveServersConfig serverConfig = config.useMasterSlaveServers()
                .setMasterAddress(normalizeAddress(masterSlave.getMasterAddress()))
                .addSlaveAddress(normalizeAddresses(masterSlave.getSlaveAddresses()))
                .setDatabase(masterSlave.getDatabase());
        applyConnectionConfig(serverConfig);

        if (StringUtils.hasText(masterSlave.getUsername())) {
            serverConfig.setUsername(masterSlave.getUsername());
        }
        if (StringUtils.hasText(masterSlave.getPassword())) {
            serverConfig.setPassword(masterSlave.getPassword());
        }
    }

    /**
     * 校验所有部署模式共享的锁和 watchdog 参数。
     */
    private void validateCommonProperties() {
        require(properties.getServerType() != null,
                "redis.hlock.server-type must not be null");
        require(properties.getWaitTime() >= -1,
                "redis.hlock.wait-time must be -1 or >= 0");
        require(properties.getLeaseTime() >= -1,
                "redis.hlock.lease-time must be -1 or >= 0");
        require(properties.getLockWatchdogTimeout() > 0,
                "redis.hlock.lock-watchdog-timeout must be > 0");
        validateConnectionProperties(properties.getConnection());
    }

    /**
     * 将所有模式通用的命令超时、建连超时和重试策略应用到 Redisson 配置
     *
     * <p>BaseConfig 是 Redisson 各种服务端配置的公共父类，因此该方法能够保证
     * Single、Cluster 和 Master-Slave 的网络行为一致。</p>
     */
    private void applyBaseConnectionConfig(BaseConfig<?> serverConfig) {
        HLockConfigProperties.Connection connection = properties.getConnection();
        serverConfig.setTimeout(connection.getTimeout())
                .setConnectTimeout(connection.getConnectTimeout())
                .setRetryAttempts(connection.getRetryAttempts())
                .setRetryInterval(connection.getRetryInterval())
                .setIdleConnectionTimeout(connection.getIdleConnectionTimeout());
    }

    /**
     * 应用单机连接池设置。单机模式的普通连接和订阅连接均由 SingleServerConfig 管理
     */
    private void applyConnectionConfig(SingleServerConfig serverConfig) {
        HLockConfigProperties.Connection connection = properties.getConnection();
        applyBaseConnectionConfig(serverConfig);
        serverConfig.setConnectionPoolSize(connection.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(connection.getConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(connection.getSubscriptionConnectionPoolSize())
                .setSubscriptionConnectionMinimumIdleSize(
                        connection.getSubscriptionConnectionMinimumIdleSize());
    }

    /**
     * 应用 Cluster、Master-Slave 共有的连接池设置
     *
     * <p>这两种模式都继承 BaseMasterSlaveServersConfig。当前使用同一组值配置
     * master 与 slave；后续若业务需要不同容量，可将 Connection 再细分为主从参数。</p>
     */
    private void applyConnectionConfig(BaseMasterSlaveServersConfig<?> serverConfig) {
        HLockConfigProperties.Connection connection = properties.getConnection();
        applyBaseConnectionConfig(serverConfig);
        serverConfig.setMasterConnectionPoolSize(connection.getConnectionPoolSize())
                .setSlaveConnectionPoolSize(connection.getConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(connection.getConnectionMinimumIdleSize())
                .setSlaveConnectionMinimumIdleSize(connection.getConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(connection.getSubscriptionConnectionPoolSize())
                .setSubscriptionConnectionMinimumIdleSize(
                        connection.getSubscriptionConnectionMinimumIdleSize());
    }

    /**
     * 校验连接池、超时和重试参数，确保错误配置在客户端创建前就能明确暴露。
     */
    private void validateConnectionProperties(HLockConfigProperties.Connection connection) {
        require(connection != null, "redis.hlock.connection must not be null");
        require(connection.getConnectionPoolSize() > 0,
                "redis.hlock.connection.connection-pool-size must be > 0");
        require(connection.getConnectionMinimumIdleSize() >= 0
                        && connection.getConnectionMinimumIdleSize() <= connection.getConnectionPoolSize(),
                "redis.hlock.connection.connection-minimum-idle-size must be between 0 and connection-pool-size");
        require(connection.getSubscriptionConnectionPoolSize() > 0,
                "redis.hlock.connection.subscription-connection-pool-size must be > 0");
        require(connection.getSubscriptionConnectionMinimumIdleSize() >= 0
                        && connection.getSubscriptionConnectionMinimumIdleSize()
                        <= connection.getSubscriptionConnectionPoolSize(),
                "redis.hlock.connection.subscription-connection-minimum-idle-size must be between 0 and subscription-connection-pool-size");
        require(connection.getTimeout() > 0,
                "redis.hlock.connection.timeout must be > 0");
        require(connection.getConnectTimeout() > 0,
                "redis.hlock.connection.connect-timeout must be > 0");
        require(connection.getRetryAttempts() >= 0,
                "redis.hlock.connection.retry-attempts must be >= 0");
        require(connection.getRetryInterval() > 0,
                "redis.hlock.connection.retry-interval must be > 0");
        require(connection.getIdleConnectionTimeout() > 0,
                "redis.hlock.connection.idle-connection-timeout must be > 0");
    }

    /**
     * 单机模式允许将 host 和 port 分开配置，也允许 host 直接填写完整地址
     */
    private String singleServerAddress(String host, int port) {
        String address = normalizeAddress(host);
        return hasExplicitPort(address) ? address : address + ":" + port;
    }

    /**
     * 补充协议地址
     */
    private String normalizeAddress(String address) {
        String normalized = address.trim();
        if (!normalized.startsWith(REDIS_PROTOCOL) && !normalized.startsWith(REDIS_SSL_PROTOCOL)) {
            normalized = REDIS_PROTOCOL + normalized;
        }
        return normalized;
    }

    /**
     * 批量标准化节点地址，并转换为 Redisson 可接收的数组
     */
    private String[] normalizeAddresses(List<String> addresses) {
        return addresses.stream()
                .map(this::normalizeAddress)
                .toArray(String[]::new);
    }

    /**
     * 校验节点列表非空、每个节点地址非空且包含端口
     */
    private void validateAddresses(List<String> addresses, String propertyName) {
        require(addresses != null && !addresses.isEmpty(),
                propertyName + " must contain at least one address");
        List<String> invalidAddresses = addresses.stream()
                .filter(address -> !StringUtils.hasText(address)
                        || !hasExplicitPort(normalizeAddress(address)))
                .collect(Collectors.toList());
        require(invalidAddresses.isEmpty(),
                propertyName + " contains blank address or address without port: " + invalidAddresses);
    }

    /**
     * 校验单个节点地址。Cluster 和主从节点必须明确端口，避免使用错误默认值
     */
    private void validateAddress(String address, String propertyName) {
        require(StringUtils.hasText(address) && hasExplicitPort(normalizeAddress(address)),
                propertyName + " must contain host and port");
    }

    private void validatePort(int port, String propertyName) {
        require(port >= 1 && port <= 65535,
                propertyName + " must be between 1 and 65535");
    }

    /**
     * 判断地址中是否明确包含端口，同时兼容 hostname、IPv4 和 [IPv6]:port
     */
    private boolean hasExplicitPort(String address) {
        String endpoint = address.substring(address.indexOf("://") + 3);
        if (endpoint.startsWith("[")) {
            int bracket = endpoint.indexOf(']');
            return bracket > 0 && endpoint.length() > bracket + 2
                    && endpoint.charAt(bracket + 1) == ':';
        }
        int colon = endpoint.lastIndexOf(':');
        return colon > 0 && colon < endpoint.length() - 1;
    }

    /**
     * 使用统一的启动异常格式，确保错误信息能直接对应 application.yml 配置项
     */
    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
