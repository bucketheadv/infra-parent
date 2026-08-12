-- 执行器支持多地址：自动注册实例表；地址字段扩容以容纳手动多地址列表。
ALTER TABLE infra_schedule_executor
    MODIFY COLUMN address TEXT NULL;

CREATE TABLE IF NOT EXISTS infra_schedule_executor_registry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    executor_id BIGINT NOT NULL,
    address VARCHAR(512) NOT NULL,
    last_heartbeat_time BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_schedule_executor_registry (executor_id, address),
    KEY idx_infra_schedule_registry_alive (executor_id, last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
