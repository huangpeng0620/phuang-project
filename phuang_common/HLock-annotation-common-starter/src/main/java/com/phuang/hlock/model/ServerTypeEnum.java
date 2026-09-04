package com.phuang.hlock.model;

/**
 * HLock 支持的 Redis 部署模式。
 *
 * <p>枚举名称直接作为 Spring Boot 配置值使用，避免数字类型的 serverType
 * 难以阅读，并让非法模式在配置绑定阶段即可被发现。</p>
 */
public enum ServerTypeEnum {

    /** 单 Redis 实例 */
    SINGLE_SERVER,

    /** Redis Cluster 集群 */
    CLUSTER_SERVERS,

    /** 固定主从拓扑，由 Redisson 连接一个主节点和一个或多个从节点。 */
    MASTER_SLAVE_SERVERS
}
