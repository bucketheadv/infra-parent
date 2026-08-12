-- 路由 ROUND 轮询游标（多调度节点共享）

CREATE TABLE IF NOT EXISTS infra_schedule_route_cursor (
    cursor_key VARCHAR(256) NOT NULL COMMENT '轮询键：executor:{id} 或执行器分组名',
    cursor_value BIGINT NOT NULL DEFAULT 0 COMMENT '累计轮询次数',
    update_time BIGINT NOT NULL DEFAULT 0 COMMENT '更新时间毫秒',
    PRIMARY KEY (cursor_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由 ROUND 轮询游标';
