package io.infra.structure.redis.commands;

import redis.clients.jedis.Jedis;

/**
 * @author qinglinl
 * Created on 2022/1/23 12:13 下午
 */
@FunctionalInterface
public interface JedisCallback<T> {
    T apply(Jedis jedis);
}
