CREATE TABLE activity_task_scheduler_instance (
    instance_id VARCHAR(128) NOT NULL COMMENT '调度应用实例唯一标识',
    last_heartbeat_time BIGINT NOT NULL COMMENT '最近一次心跳时间戳，单位毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (instance_id),
    KEY idx_activity_task_scheduler_instance_heartbeat (last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务调度应用实例心跳表';
