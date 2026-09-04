# custom-annotation-common-start

基于 Spring AOP 和 Redisson 实现的注解式分布式锁组件。业务方法添加 `@HLock` 后，组件会根据 SpEL 表达式生成 Redis 锁名，在方法执行前获取锁，并在方法结束后释放锁。

当前版本支持：

- Redis 单机模式；
- Redis Cluster 模式；
- Redis 固定 Master-Slave 模式；
- 可重入锁、公平锁、读锁和写锁；
- 全局及方法级等待时间、租约时间；
- Redisson watchdog 自动续期；
- 连接池、命令超时、建连超时和重试参数；
- 业务项目自定义 `RedissonClient`；
- 获取锁、释放锁和客户端初始化日志。

## 1. 使用场景

- 防止接口重复提交；
- 防止同一个订单、用户或商品被并发处理；
- 控制多个服务实例对共享资源的访问；
- 保证定时任务在多个实例中只由一个实例执行；
- 使用读写锁控制共享资源的读写并发。

分布式锁不能替代数据库唯一索引、业务幂等校验、事务和状态校验。

## 2. 环境要求

- JDK 8+；
- Spring Boot 2.7.x；
- Redis；
- Maven。

项目当前主要依赖：

| 依赖 | 版本 |
| --- | --- |
| Spring Boot AutoConfigure | `2.7.4` |
| Spring Boot AOP | `2.7.4` |
| Redisson | `3.17.7` |
| Lombok | `1.18.20` |

## 3. 引入组件

先将项目安装到本地 Maven 仓库：

```bash
mvn clean install
```

业务项目添加依赖：

```xml
<dependency>
    <groupId>com.phuang</groupId>
    <artifactId>custom-annotation-common-start</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

组件通过 `META-INF/spring.factories` 注册自动配置，并自动导入：

- `HLockAnnotationAspect`；
- `LockInfoHandler`；
- `LockFactory`。

正常情况下不需要额外配置 `@ComponentScan`。

如果业务项目已经声明 `RedissonClient` Bean，组件会通过 `@ConditionalOnMissingBean` 复用该 Bean，不再根据 `redis.hlock` 创建新的客户端。

## 4. 执行流程

```text
调用带 @HLock 的 Spring Bean 方法
        |
        v
HLockAnnotationAspect 拦截方法
        |
        v
LockInfoHandler 解析 SpEL 和锁参数
        |
        v
LockFactory 根据 lockType 获取 Redisson RLock
        |
        v
tryLock(waitTime, leaseTime, unit)
        |
        |-- 获取失败：抛出 BusinessException，错误码 1001
        |
        `-- 获取成功：执行业务方法
                         |
                         v
             当前线程仍持有锁时执行 unlock()
```

切面通过 `@Order(0)` 放在事务切面外层，目标是先获取分布式锁，再进入事务，事务结束后再释放锁。

## 5. Redis 配置

所有配置使用前缀：

```text
redis.hlock
```

### 5.1 单机模式

单机模式是默认模式，未指定 `server-type` 时自动使用 `SINGLE_SERVER`。

```yaml
redis:
  hlock:
    server-type: SINGLE_SERVER
    wait-time: -1
    lease-time: -1
    lock-watchdog-timeout: 30000
    single-server:
      host: 127.0.0.1
      port: 6379
      database: 0
      username:
      password:
```

`host` 支持以下格式：

```text
127.0.0.1
redis://127.0.0.1
redis://127.0.0.1:6379
rediss://127.0.0.1:6379
```

当 `host` 已经包含端口时，`single-server.port` 不再追加到地址中。

### 5.2 Cluster 模式

```yaml
redis:
  hlock:
    server-type: CLUSTER_SERVERS
    wait-time: -1
    lease-time: -1
    lock-watchdog-timeout: 30000
    cluster-servers:
      node-addresses:
        - redis://10.0.0.11:7000
        - redis://10.0.0.12:7001
        - redis://10.0.0.13:7002
      scan-interval: 1000
      username:
      password: your-password
```

注意：

- 至少配置一个种子节点；
- 每个节点地址必须包含端口；
- Redis Cluster 只支持 database 0，因此 Cluster 配置不提供 `database`；
- `scan-interval` 是 Redisson 扫描集群拓扑的间隔，单位毫秒。

### 5.3 Master-Slave 模式

```yaml
redis:
  hlock:
    server-type: MASTER_SLAVE_SERVERS
    wait-time: -1
    lease-time: -1
    lock-watchdog-timeout: 30000
    master-slave-servers:
      master-address: redis://10.0.0.21:6379
      slave-addresses:
        - redis://10.0.0.22:6379
        - redis://10.0.0.23:6379
      database: 0
      username:
      password: your-password
```

注意：

- 必须配置一个主节点；
- 至少配置一个从节点；
- 所有节点地址必须包含端口；
- 这是固定主从拓扑，不支持 Sentinel 自动发现和主节点切换。

### 5.4 连接池、超时和重试

以下配置对三种 Redis 模式统一生效：

```yaml
redis:
  hlock:
    connection:
      connection-pool-size: 64
      connection-minimum-idle-size: 24
      subscription-connection-pool-size: 50
      subscription-connection-minimum-idle-size: 1
      timeout: 3000
      connect-timeout: 10000
      retry-attempts: 3
      retry-interval: 1500
      idle-connection-timeout: 10000
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `connection-pool-size` | `64` | 单节点普通连接池最大连接数 |
| `connection-minimum-idle-size` | `24` | 普通连接池最小空闲连接数 |
| `subscription-connection-pool-size` | `50` | Pub/Sub 连接池最大连接数 |
| `subscription-connection-minimum-idle-size` | `1` | Pub/Sub 最小空闲连接数 |
| `timeout` | `3000` | Redis 命令响应超时，单位毫秒 |
| `connect-timeout` | `10000` | TCP 建连超时，单位毫秒 |
| `retry-attempts` | `3` | 命令失败后的重试次数，`0` 表示不重试 |
| `retry-interval` | `1500` | 重试间隔，单位毫秒 |
| `idle-connection-timeout` | `10000` | 空闲连接回收超时，单位毫秒 |

Cluster 和 Master-Slave 模式会将 `connection-pool-size` 和 `connection-minimum-idle-size` 同时应用到主、从节点连接池。

### 5.5 公共锁配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server-type` | `SINGLE_SERVER` | Redis 部署模式 |
| `wait-time` | `-1` | 注解未设置等待时间时使用的全局值 |
| `lease-time` | `-1` | 注解未设置租约时间时使用的全局值 |
| `lock-watchdog-timeout` | `30000` | Redisson watchdog 超时时间，单位毫秒 |

组件在创建 `RedissonClient` 前校验配置。地址、端口、节点数量、连接池或超时参数错误时，应用会在启动阶段失败，并在异常消息中指出具体配置项。

## 6. 注解用法

### 6.1 按简单参数加锁

```java
import com.phuang.hlock.annotation.HLock;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @HLock(key = "#orderId")
    public void pay(Long orderId) {
        // 同一个 orderId 同一时间只允许一个实例执行
    }
}
```

### 6.2 按对象属性加锁

```java
@HLock(key = "#request.orderId")
public void create(CreateOrderRequest request) {
}
```

当前 SpEL 实现通过方法参数名注册变量。业务项目应开启 Java 参数名保留：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

### 6.3 让不同方法竞争同一资源

默认锁前缀包含类名和方法名，不同方法默认不会竞争同一把锁。需要跨方法互斥时，显式设置相同 `prefixKey`：

```java
@HLock(prefixKey = "inventory", key = "#skuId")
public void deduct(Long skuId) {
}

@HLock(prefixKey = "inventory", key = "#skuId")
public void restore(Long skuId) {
}
```

### 6.4 方法级等待时间和租约时间

```java
import java.util.concurrent.TimeUnit;

@HLock(
        prefixKey = "coupon",
        key = "#userId",
        waitTime = 2,
        leaseTime = 10,
        unit = TimeUnit.SECONDS
)
public void receive(Long userId) {
}
```

该方法最多等待 2 秒获取锁，获取成功后锁租约为 10 秒。方法级参数优先于全局配置。

固定 `leaseTime` 必须大于业务方法的最长执行时间，否则锁可能在业务执行完成前失效。业务耗时无法准确预估时，建议使用 `leaseTime = -1` 启用 watchdog。

### 6.5 公平锁

```java
@HLock(key = "#orderId", lockType = LockType.Fair)
public void process(Long orderId) {
}
```

公平锁按照请求锁的先后顺序分配锁，但通常比普通可重入锁开销更高。

### 6.6 读写锁

```java
@HLock(prefixKey = "document", key = "#documentId", lockType = LockType.Read)
public Document query(Long documentId) {
    return repository.find(documentId);
}

@HLock(prefixKey = "document", key = "#documentId", lockType = LockType.Write)
public void update(Long documentId) {
    repository.update(documentId);
}
```

读锁之间可以并发；写锁会与读锁及其他写锁互斥。读写方法必须生成相同锁名，才能属于同一个读写锁组。

## 7. `@HLock` 参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `key` | 无 | 必填，SpEL 业务 key 表达式 |
| `prefixKey` | 当前类名和方法名 | 锁名前缀，跨方法互斥时显式指定 |
| `waitTime` | `-1` | 等待锁时间；`-1` 使用全局配置 |
| `leaseTime` | `-1` | 锁租约时间；`-1` 使用全局配置 |
| `unit` | `SECONDS` | 方法级等待时间和租约时间单位 |
| `lockType` | `Reentrant` | 锁类型 |

支持的锁类型：

| 类型 | Redisson 实现 | 说明 |
| --- | --- | --- |
| `Reentrant` | `getLock()` | 普通可重入锁，默认类型 |
| `Fair` | `getFairLock()` | 公平锁 |
| `Read` | `getReadWriteLock().readLock()` | 读锁 |
| `Write` | `getReadWriteLock().writeLock()` | 写锁 |

## 8. 锁名规则

锁名格式：

```text
HLOCK_{businessPrefix}_{businessKey}
```

- `businessPrefix`：`prefixKey` 非空时使用配置值，否则使用声明类和方法名；
- `businessKey`：SpEL 表达式计算结果。

业务 key 应满足：

- 同一个业务资源始终生成相同值；
- 不同资源尽量生成不同值；
- 不使用随机数或当前时间；
- 避免包含敏感信息和超长文本。

## 9. 异常行为

获取锁失败时抛出：

```text
BusinessException
errorCode: 1001
message: 请稍后重试
```

业务方法本身抛出的异常会原样向上传播。切面不会将业务异常统一转换为系统异常。

建议业务项目通过统一异常处理器读取：

```java
exception.getErrorCode()
exception.getMessage()
```

## 10. 日志

组件会输出以下日志：

| 级别 | 场景 |
| --- | --- |
| `INFO` | RedissonClient 开始初始化、初始化完成 |
| `DEBUG` | 开始获取锁、获取成功、释放成功 |
| `WARN` | 获取失败、线程中断、当前线程不再持有锁 |
| `ERROR` | 释放锁异常 |

调试锁问题时可以临时开启：

```yaml
logging:
  level:
    com.phuang.hlock: DEBUG
```

锁名可能包含业务 key，不建议在生产环境长期打开 DEBUG 日志。

## 11. 使用注意事项

1. `@HLock` 必须添加在 Spring 管理的 Bean 方法上。
2. 同一个类中通过 `this.method()` 调用不会经过 Spring AOP 代理，锁不会生效。
3. `private`、`final` 方法以及手动创建的对象不能保证切面生效。
4. 当前 SpEL 使用参数名，业务项目需要开启 `-parameters`。
5. 固定租约必须覆盖业务最长执行时间，否则锁提前到期后切面会跳过释放并记录 WARN。
6. Cluster 模式只使用 database 0。
7. Master-Slave 是固定拓扑，不提供 Sentinel 故障转移能力。
8. Redis 不可用时，加锁操作可能抛出 Redisson 连接或超时异常。
9. 当前自动配置机制面向 Spring Boot 2.x，尚未提供 Spring Boot 3 的 `AutoConfiguration.imports`。

## 12. 项目结构

```text
src/main/java/com/phuang
├── common/utils
│   ├── AssertUtils.java
│   └── SpElUtils.java
└── hlock
    ├── annotation/HLock.java
    ├── aspect/HLockAnnotationAspect.java
    ├── config
    │   ├── HLockConfigProperties.java
    │   └── HLockRedissonConfig.java
    ├── handler/LockInfoHandler.java
    └── model
        ├── BusinessException.java
        ├── LockFactory.java
        ├── LockInfo.java
        ├── ServerTypeEnum.java
        └── enums
            ├── BusinessErrorEnum.java
            ├── ErrorEnum.java
            └── LockType.java
```

## 13. 开发验证

执行构建：

```bash
mvn clean test
```

建议重点验证：

- 相同业务 key 是否互斥；
- 不同业务 key 是否可以并发；
- 获取锁失败是否返回错误码 `1001`；
- 业务异常是否原样传播；
- 业务执行完成或抛出异常后是否释放锁；
- Single、Cluster、Master-Slave 配置能否正确创建客户端；
- 自定义 `RedissonClient` 是否被正确复用；
- watchdog 和固定租约是否符合预期。

## 14. 当前限制与后续方向

- 增加 Sentinel 和 Replicated 模式；
- 支持不依赖参数名的 `#p0`、`#a0` SpEL 写法；
- 增加配置元数据，让 IDE 自动提示 `redis.hlock.*`；
- 增加单元测试、自动配置测试和 Redis 集成测试；
- 增加 Spring Boot 3 兼容版本；
- 增加可配置的获取锁失败处理器和监控指标。
