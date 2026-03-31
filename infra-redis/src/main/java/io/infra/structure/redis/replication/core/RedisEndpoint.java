package io.infra.structure.redis.replication.core;

import lombok.Getter;
import redis.clients.jedis.JedisPool;

/**
 * Jedis 连接端点封装。
 *
 * @author codex
 */
@Getter
public class RedisEndpoint {
    /**
     * 所属节点组名称
     */
    private final String nodeGroupName;

    /**
     * 节点地址，格式 host:port
     */
    private final String address;

    /**
     * 是否为主节点
     */
    private final boolean master;

    /**
     * Jedis 连接池
     */
    private final JedisPool pool;

    /**
     * 故障隔离截止时间戳
     */
    private volatile long isolatedUntilMillis = 0L;

    public RedisEndpoint(String nodeGroupName, String address, boolean master, JedisPool pool) {
        this.nodeGroupName = nodeGroupName;
        this.address = address;
        this.master = master;
        this.pool = pool;
    }

    public boolean isAvailable(long now) {
        return now >= isolatedUntilMillis;
    }

    public void markFailure(long cooldownMillis) {
        this.isolatedUntilMillis = System.currentTimeMillis() + Math.max(cooldownMillis, 0L);
    }

    public void markSuccess() {
        this.isolatedUntilMillis = 0L;
    }
}
