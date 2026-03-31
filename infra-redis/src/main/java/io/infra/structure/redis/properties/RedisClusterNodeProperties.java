package io.infra.structure.redis.properties;

import lombok.Data;

/**
 * Redis Cluster 种子节点配置。
 *
 * @author codex
 */
@Data
public class RedisClusterNodeProperties {
    /**
     * 节点 host
     */
    private String host;

    /**
     * 节点 port
     */
    private int port = 6379;
}
