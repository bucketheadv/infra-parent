package io.infra.structure.redis.cluster.core;

import lombok.Getter;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.Set;

/**
 * Redis Cluster 模板。
 *
 * @author codex
 */
@Getter
public class JedisClusterTemplate extends JedisCluster {
    /**
     * template 名称
     */
    private final String name;

    public JedisClusterTemplate(String name,
                                Set<HostAndPort> clusterNodes,
                                JedisClientConfig clientConfig,
                                int maxAttempts,
                                GenericObjectPoolConfig<Connection> poolConfig) {
        super(clusterNodes, clientConfig, maxAttempts, poolConfig);
        this.name = name;
    }
}
