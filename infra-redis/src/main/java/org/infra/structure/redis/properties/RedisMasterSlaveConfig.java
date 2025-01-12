package org.infra.structure.redis.properties;

import lombok.Data;
import org.infra.structure.redis.constants.LoadBalanceEnum;

import java.util.List;

/**
 * @author sven
 * Created on 2022/2/28 11:13 下午
 */
@Data
public class RedisMasterSlaveConfig {
    /**
     * 主库配置
     */
    private RedisProperties master;

    /**
     * 从库负载均衡算法
     * 轮询算法会先ping，ping不通时自动跳到下一节点
     */
    private LoadBalanceEnum loadBalance = LoadBalanceEnum.round_robin;

    /**
     * 从库配置
     */
    private List<RedisProperties> slaves;
}
