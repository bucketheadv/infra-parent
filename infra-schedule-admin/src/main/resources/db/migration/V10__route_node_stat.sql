-- 路由 LFU/LRU 统计表，供多调度节点共享

CREATE TABLE IF NOT EXISTS infra_schedule_route_stat (
    node_key VARCHAR(512) NOT NULL COMMENT '路由节点键：address 或 local:{executorId}',
    use_count INT NOT NULL DEFAULT 0 COMMENT '累计被路由选中次数',
    last_route_time BIGINT NOT NULL DEFAULT 0 COMMENT '最近一次被路由选中时间毫秒',
    update_time BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间毫秒',
    PRIMARY KEY (node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器路由 LFU/LRU 统计';
