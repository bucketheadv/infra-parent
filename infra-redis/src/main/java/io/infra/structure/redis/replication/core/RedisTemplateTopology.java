package io.infra.structure.redis.replication.core;

import io.infra.structure.redis.constants.LoadBalanceEnum;
import io.infra.structure.redis.properties.RedisTemplateConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * RedisTemplate 运行时拓扑。
 *
 * @author codex
 */
@Getter
public class RedisTemplateTopology {
    /**
     * 读负载均衡策略
     */
    private final LoadBalanceEnum loadBalance;

    /**
     * 读偏好
     */
    private final RedisTemplateConfig.ReadPreference readPreference;

    /**
     * 最大尝试次数
     */
    private final int maxAttempts;

    /**
     * 重试间隔
     */
    private final long retryIntervalMillis;

    /**
     * 故障节点隔离时长
     */
    private final long failoverCooldownMillis;

    /**
     * 节点恢复前是否主动探活
     */
    private final boolean probeOnRecover;

    /**
     * template 对应的所有节点组
     */
    private final List<RedisNodeGroup> nodeGroups;

    public RedisTemplateTopology(LoadBalanceEnum loadBalance,
                                 RedisTemplateConfig.ReadPreference readPreference,
                                 int maxAttempts,
                                 long retryIntervalMillis,
                                 long failoverCooldownMillis,
                                 boolean probeOnRecover,
                                 List<RedisNodeGroup> nodeGroups) {
        this.loadBalance = loadBalance;
        this.readPreference = readPreference;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.retryIntervalMillis = Math.max(retryIntervalMillis, 0L);
        this.failoverCooldownMillis = Math.max(failoverCooldownMillis, 0L);
        this.probeOnRecover = probeOnRecover;
        this.nodeGroups = nodeGroups == null ? List.of() : List.copyOf(nodeGroups);
    }

    public List<RedisEndpoint> getMasterEndpoints() {
        List<RedisEndpoint> result = new ArrayList<>();
        for (RedisNodeGroup nodeGroup : nodeGroups) {
            result.add(nodeGroup.getMaster());
        }
        return result;
    }

    public List<RedisEndpoint> getReplicaEndpoints() {
        List<RedisEndpoint> result = new ArrayList<>();
        for (RedisNodeGroup nodeGroup : nodeGroups) {
            result.addAll(nodeGroup.getReadableReplicas());
        }
        return result;
    }
}
