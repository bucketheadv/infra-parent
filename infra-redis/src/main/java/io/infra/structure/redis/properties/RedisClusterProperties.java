package io.infra.structure.redis.properties;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis Cluster 配置。
 *
 * @author codex
 */
@Data
public class RedisClusterProperties {
    /**
     * 种子节点列表
     */
    private List<RedisClusterNodeProperties> nodes = new ArrayList<>();

    /**
     * cluster 客户端公共配置
     */
    private RedisProperties client = new RedisProperties();

    /**
     * JedisCluster 最大尝试次数
     */
    private int maxAttempts = 5;
}
