package io.infra.structure.redis.properties;

import io.infra.structure.redis.constants.LoadBalanceEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sven
 * Created on 2022/2/28 11:13 下午
 */
@Data
public class RedisTemplateConfig {
    /**
     * template 部署模式
     */
    public enum Mode {
        /**
         * 主从
         */
        replication,
        /**
         * 集群
         */
        cluster
    }

    /**
     * 读路由策略
     */
    public enum ReadPreference {
        /**
         * 只从主节点读取
         */
        master,

        /**
         * 优先从主节点读取，主节点不可用时回退到从节点
         */
        master_preferred,

        /**
         * 只从从节点读取
         */
        replica,

        /**
         * 优先从从节点读取，从节点不可用时回退到主节点
         */
        replica_preferred
    }

    /**
     * Redis 部署模式
     */
    private Mode mode = Mode.replication;

    /**
     * 从库负载均衡算法
     */
    private LoadBalanceEnum loadBalance = LoadBalanceEnum.round_robin;

    /**
     * 主节点配置
     */
    private RedisProperties master = new RedisProperties();

    /**
     * 从库配置
     */
    private List<RedisProperties> slaves = new ArrayList<>();

    /**
     * 读操作默认从从库读
     */
    private ReadPreference readPreference = ReadPreference.replica_preferred;

    /**
     * 最大重试轮次，包含首次执行
     */
    private int maxAttempts = 2;

    /**
     * 两轮重试之间的等待时间
     */
    private long retryIntervalMillis = 50L;

    /**
     * 节点失败后的隔离时间
     */
    private long failoverCooldownMillis = 3_000L;

    /**
     * 从故障中恢复时是否主动 ping 探测
     */
    private boolean probeOnRecover = true;

    /**
     * cluster 配置
     */
    private RedisClusterProperties cluster = new RedisClusterProperties();
}
