package io.infra.structure.redis.replication.core;

import io.infra.structure.redis.constants.LoadBalanceEnum;
import io.infra.structure.redis.properties.RedisTemplateConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负责根据读写偏好和负载均衡策略生成候选节点列表。
 *
 * @author codex
 */
public class RedisEndpointSelector {
    /**
     * template 拓扑信息
     */
    private final RedisTemplateTopology topology;

    /**
     * round-robin 计数器
     */
    private final AtomicLong counter = new AtomicLong(0);

    public RedisEndpointSelector(RedisTemplateTopology topology) {
        this.topology = topology;
    }

    public List<RedisEndpoint> selectCandidates(boolean readOperation) {
        List<RedisEndpoint> masters = order(topology.getMasterEndpoints());
        if (!readOperation) {
            return masters;
        }

        List<RedisEndpoint> replicas = order(topology.getReplicaEndpoints());
        RedisTemplateConfig.ReadPreference readPreference = topology.getReadPreference();
        if (readPreference == RedisTemplateConfig.ReadPreference.master) {
            return masters;
        }
        if (readPreference == RedisTemplateConfig.ReadPreference.master_preferred) {
            return concat(masters, replicas);
        }
        if (readPreference == RedisTemplateConfig.ReadPreference.replica) {
            return replicas;
        }
        return concat(replicas, masters);
    }

    private List<RedisEndpoint> order(List<RedisEndpoint> endpoints) {
        if (endpoints.isEmpty()) {
            return List.of();
        }
        List<RedisEndpoint> ordered = new ArrayList<>(endpoints);
        if (topology.getLoadBalance() == LoadBalanceEnum.random) {
            Collections.shuffle(ordered, ThreadLocalRandom.current());
            return ordered;
        }
        int start = Math.floorMod(counter.getAndIncrement(), ordered.size());
        if (start == 0) {
            return ordered;
        }
        List<RedisEndpoint> rotated = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            rotated.add(ordered.get((start + i) % ordered.size()));
        }
        return rotated;
    }

    private List<RedisEndpoint> concat(List<RedisEndpoint> first, List<RedisEndpoint> second) {
        List<RedisEndpoint> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        for (RedisEndpoint endpoint : second) {
            if (!result.contains(endpoint)) {
                result.add(endpoint);
            }
        }
        return result;
    }
}
