package io.infra.structure.redis.replication.core;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一套主从节点组。
 *
 * @author codex
 */
@Getter
public class RedisNodeGroup {
    /**
     * 节点组名称
     */
    private final String name;

    /**
     * 主节点
     */
    private final RedisEndpoint master;

    /**
     * 从节点列表
     */
    private final List<RedisEndpoint> slaves;

    public RedisNodeGroup(String name, RedisEndpoint master, List<RedisEndpoint> slaves) {
        this.name = name;
        this.master = master;
        this.slaves = slaves == null ? List.of() : List.copyOf(slaves);
    }

    public List<RedisEndpoint> getReadableReplicas() {
        return slaves;
    }

    public List<RedisEndpoint> getAllReadableEndpoints() {
        List<RedisEndpoint> result = new ArrayList<>(slaves);
        result.add(master);
        return Collections.unmodifiableList(result);
    }
}
