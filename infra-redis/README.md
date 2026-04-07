# infra-redis 使用说明

## 概述

`infra-redis` 基于 `Jedis` 实现，支持：

- 配置多个 `JedisTemplate`
- 配置多个 `JedisClusterTemplate`
- 每个主从 template 配置一主多从
- 读操作默认优先走从库
- 通过 `@Qualifier` 指定不同 template
- 读负载均衡、节点探活、重试、故障隔离和主库回退

## 包结构

- 主从/读写分离实现位于 `io.infra.structure.redis.replication.*`
- Cluster 实现位于 `io.infra.structure.redis.cluster.*`
- 主从模板接口和回调位于 `io.infra.structure.redis.replication.*`
- 配置位于 `io.infra.structure.redis.properties`

## 快速开始

### 1. 开启 Redis

```properties
infra.redis.enabled=true
infra.redis.primary=main
```

### 2. 配置多个 template

日常推荐直接使用简化配置，也就是每个 template 直接配置：

- `master`
- `slaves`

例如：

```properties
infra.redis.template.main.readPreference=replica_preferred
infra.redis.template.main.loadBalance=round_robin
infra.redis.template.main.maxAttempts=3
infra.redis.template.main.retryIntervalMillis=100
infra.redis.template.main.failoverCooldownMillis=3000
infra.redis.template.main.probeOnRecover=true

infra.redis.template.main.master.host=127.0.0.1
infra.redis.template.main.master.port=6379
infra.redis.template.main.master.db=0
infra.redis.template.main.master.connectionTimeoutMillis=2000
infra.redis.template.main.master.socketTimeoutMillis=2000

infra.redis.template.main.slaves[0].host=127.0.0.1
infra.redis.template.main.slaves[0].port=6380
infra.redis.template.main.slaves[1].host=127.0.0.1
infra.redis.template.main.slaves[1].port=6381

infra.redis.template.audit.readPreference=master_preferred
infra.redis.template.audit.loadBalance=random
infra.redis.template.audit.master.host=127.0.0.1
infra.redis.template.audit.master.port=7379
infra.redis.template.audit.slaves[0].host=127.0.0.1
infra.redis.template.audit.slaves[0].port=7380
```

这已经可以满足绝大多数“多个 template，每个 template 一主多从”的场景。

### 3. 配置 Redis Cluster template

Cluster 模式下，单个 template 使用 `mode=cluster`，并配置种子节点：

```properties
infra.redis.template.order.mode=cluster
infra.redis.template.order.cluster.nodes[0].host=127.0.0.1
infra.redis.template.order.cluster.nodes[0].port=7001
infra.redis.template.order.cluster.nodes[1].host=127.0.0.1
infra.redis.template.order.cluster.nodes[1].port=7002
infra.redis.template.order.cluster.nodes[2].host=127.0.0.1
infra.redis.template.order.cluster.nodes[2].port=7003

infra.redis.template.order.cluster.client.password=
infra.redis.template.order.cluster.client.clientName=order-cluster
infra.redis.template.order.cluster.client.connectionTimeoutMillis=2000
infra.redis.template.order.cluster.client.socketTimeoutMillis=2000
infra.redis.template.order.cluster.client.blockingSocketTimeoutMillis=2000
infra.redis.template.order.cluster.client.maxTotal=16
infra.redis.template.order.cluster.client.maxIdle=8
infra.redis.template.order.cluster.client.minIdle=4
infra.redis.template.order.cluster.maxAttempts=5
```

## 注入方式

默认主 template 可以直接按类型注入：

```java
@Service
public class CacheService {
    private final JedisTemplate jedisTemplate;

    public CacheService(JedisTemplate jedisTemplate) {
        this.jedisTemplate = jedisTemplate;
    }
}
```

多个 template 使用 `@Qualifier`：

```java
@Service
public class MultiRedisService {
    private final JedisTemplate mainTemplate;
    private final JedisTemplate auditTemplate;

    public MultiRedisService(@Qualifier("mainJedisTemplate") JedisTemplate mainTemplate,
                             @Qualifier("auditJedisTemplate") JedisTemplate auditTemplate) {
        this.mainTemplate = mainTemplate;
        this.auditTemplate = auditTemplate;
    }
}
```

如果你的 `JedisTemplate` 是通过配置动态注册，IDEA 可能提示找不到 Bean（运行时通常正常）。  
推荐注入 `RedisTemplateProvider` 来规避静态分析误报：

```java
import io.infra.structure.redis.core.RedisTemplateProvider;

@Service
public class CacheService {
    private final JedisTemplate mainTemplate;
    private final JedisTemplate auditTemplate;

    public CacheService(RedisTemplateProvider provider) {
        this.mainTemplate = provider.jedisTemplate("main");
        this.auditTemplate = provider.jedisTemplate("audit");
    }
}
```

同理，cluster template 也可以通过 provider 获取：

```java
JedisClusterTemplate orderTemplate = provider.jedisClusterTemplate("order");
```

Cluster template 使用 `JedisClusterTemplate` 注入：

```java
@Service
public class ClusterCacheService {
    private final JedisClusterTemplate orderTemplate;

    public ClusterCacheService(@Qualifier("orderJedisClusterTemplate") JedisClusterTemplate orderTemplate) {
        this.orderTemplate = orderTemplate;
    }
}
```

## 路由策略

### 读写分离

- 写操作默认只走主节点
- 读操作默认按 `readPreference=replica_preferred` 优先走从节点
- 当所有从节点不可用时会自动回退到主节点

### readPreference

- `master`：读写都走主节点
- `master_preferred`：优先主节点，失败后尝试从节点
- `replica`：只读从节点
- `replica_preferred`：优先从节点，失败后回退主节点

### loadBalance

- `round_robin`：轮询选择候选节点
- `random`：随机打散候选节点

## 容错策略

每个 template 支持以下容错配置：

- `maxAttempts`：最大尝试次数
- `retryIntervalMillis`：两次尝试之间的等待时间
- `failoverCooldownMillis`：节点失败后隔离时长
- `probeOnRecover`：恢复前是否执行 `PING` 探活

执行过程大致如下：

1. 根据读写类型选择候选节点
2. 按负载均衡策略排序
3. 节点失败后临时隔离
4. 当前轮失败时尝试其他节点
5. 一轮失败后按配置重试
6. 读请求在需要时回退到主节点

## 节点参数

每个节点支持以下常用配置：

- `host`
- `port`
- `password`
- `db`
- `clientName`
- `connectionTimeoutMillis`
- `socketTimeoutMillis`
- `blockingSocketTimeoutMillis`
- `maxTotal`
- `maxIdle`
- `minIdle`
- `testOnCreate`
- `testOnBorrow`
- `testOnReturn`
- `testWhileIdle`
- `jmxEnabled`
- `jmxNamePrefix`
- `jmxNameBase`

## 配置建议

推荐优先使用：

```properties
infra.redis.template.main.master.host=127.0.0.1
infra.redis.template.main.master.port=6379
infra.redis.template.main.slaves[0].host=127.0.0.1
infra.redis.template.main.slaves[0].port=6380
```

当前实现直接使用 `master/slaves` 作为唯一推荐配置方式，不需要额外写 `nodes[0]`。

## 当前实现说明

- `JedisTemplate` 位于 `io.infra.structure.redis.replication.core`
- `Bean` 命名规则保持为 `配置名 + JedisTemplate`
- `Cluster Bean` 命名规则为 `配置名 + JedisClusterTemplate`
- stream consumer group 相关 `xreadGroup*` 操作固定走主节点
- `evalReadonly` / `evalshaReadonly` 会走读路径
- `mode=cluster` 时会注册 `JedisClusterTemplate`
