package org.infra.structure.redis.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author qinglin.liu
 * created at 2024/3/15 10:06
 */
@Getter
@AllArgsConstructor
public enum LoadBalanceEnum {
    random("random", "随机算法"),
    round_robin("round_robin", "轮询算法"),
    ;
    private final String code;

    private final String desc;
}
