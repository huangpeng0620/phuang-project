package com.phuang.hlock.config;

import com.phuang.hlock.model.ServerTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HLock 使用的 Redisson 配置。
 *
 * <p>锁本身的公共参数放在根节点，不同 Redis 部署模式的连接参数放在各自的配置块中。
 * 这样可以避免 Cluster、Master-Slave 复用单机配置字段，也便于后续继续扩展 Sentinel。</p>
 */
@Data
@ConfigurationProperties(prefix = HLockConfigProperties.PREFIX)
public class HLockConfigProperties {

    public static final String PREFIX = "redis.hlock";

    /**
     * Redis 部署模式。配置文件可填写 SINGLE_SERVER、CLUSTER_SERVERS 或
     * MASTER_SLAVE_SERVERS，默认使用单机模式，兼容原有使用方式。
     */
    private ServerTypeEnum serverType = ServerTypeEnum.SINGLE_SERVER;

    /**
     * 全局获取锁等待时间。注解未显式设置 waitTime 时使用该值。
     */
    private long waitTime = -1;

    /**
     * 全局锁租约时间。-1 表示启用 Redisson watchdog 自动续期。
     */
    private long leaseTime = -1;

    /**
     * watchdog 超时时间，单位为毫秒，仅在 leaseTime=-1 时参与自动续期。
     */
    private long lockWatchdogTimeout = 30000;

    /**
     * Redisson 连接、超时和重试参数。
     *
     * <p>该配置对 Single、Cluster 和 Master-Slave 三种模式统一生效。
     * 在 Cluster 和 Master-Slave 模式下，connectionPoolSize 会同时作为主、从节点
     * 的连接池上限使用，以保持默认策略简单一致。</p>
     */
    private Connection connection = new Connection();

    /**
     * 单机模式配置。提供默认 localhost 地址，使未配置连接信息时仍保持原有行为。
     */
    private SingleServer singleServer = new SingleServer();

    /**
     * Redis Cluster 模式配置。
     */
    private ClusterServers clusterServers = new ClusterServers();

    /**
     * Redis 主从模式配置。
     */
    private MasterSlaveServers masterSlaveServers = new MasterSlaveServers();

    @Data
    public static class Connection {

        /**
         * 单个 Redis 节点的普通连接池最大连接数。
         * Cluster、Master-Slave 模式会分别为每个节点应用该上限。
         */
        private int connectionPoolSize = 64;

        /** 普通连接池保持的最小空闲连接数 */
        private int connectionMinimumIdleSize = 24;

        /** Pub/Sub 订阅连接池最大连接数 */
        private int subscriptionConnectionPoolSize = 50;

        /** Pub/Sub 订阅连接池保持的最小空闲连接数 */
        private int subscriptionConnectionMinimumIdleSize = 1;

        /**
         * 单次 Redis 命令响应超时，单位毫秒；超时后由 retryAttempts 决定是否重试
         */
        private int timeout = 3000;

        /** 建立 TCP 连接超时，单位毫秒 */
        private int connectTimeout = 10000;

        /**
         * 单个 Redis 命令失败后的最大重试次数,0 表示不重试
         */
        private int retryAttempts = 3;

        /** 重试间隔，单位毫秒 */
        private int retryInterval = 1500;

        /** 空闲连接在被回收前允许保持的最长时间,单位毫秒 */
        private int idleConnectionTimeout = 10000;
    }

    @Data
    public static class SingleServer {

        /**
         * Redis 主机或完整地址，例如 localhost、redis://localhost 或
         * rediss://redis.example.com:6379。
         */
        private String host = "localhost";

        /** Redis 端口；host 已包含端口时忽略该值。 */
        private int port = 6379;

        /** Redis database，单机模式允许选择非 0 database。 */
        private int database = 0;

        /** Redis ACL 用户名，未启用 ACL 时保持为空。 */
        private String username;

        /** Redis 密码，无密码时保持为空。 */
        private String password;
    }

    @Data
    public static class ClusterServers {

        /**
         * Cluster 节点地址列表。每个地址应包含端口，协议可省略；至少配置一个节点。
         */
        private List<String> nodeAddresses = new ArrayList<>();

        /** Cluster 拓扑扫描间隔，单位为毫秒。 */
        private int scanInterval = 1000;

        /** Redis ACL 用户名，集群所有节点应使用一致的认证信息。 */
        private String username;

        /** Redis 密码，集群所有节点应使用一致的认证信息。 */
        private String password;
    }

    @Data
    public static class MasterSlaveServers {

        /** 主节点地址，必须包含端口，协议可省略。 */
        private String masterAddress;

        /** 从节点地址列表，至少配置一个从节点。 */
        private List<String> slaveAddresses = new ArrayList<>();

        /** Redis database，主节点和所有从节点必须使用相同 database。 */
        private int database = 0;

        /** Redis ACL 用户名，主从节点应使用一致的认证信息。 */
        private String username;

        /** Redis 密码，主从节点应使用一致的认证信息。 */
        private String password;
    }
}
