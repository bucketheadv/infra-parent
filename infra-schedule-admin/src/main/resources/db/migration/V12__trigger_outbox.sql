CREATE TABLE IF NOT EXISTS infra_schedule_trigger_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '可靠触发记录主键',
    job_id BIGINT NOT NULL COMMENT '所属任务主键',
    trigger_time BIGINT NOT NULL COMMENT '本次计划或手动触发时间戳毫秒',
    status VARCHAR(16) NOT NULL COMMENT '投递状态：PENDING/PROCESSING/DISPATCHED/CANCELLED',
    claim_owner VARCHAR(128) NULL COMMENT '当前投递租约持有节点',
    claim_until BIGINT NULL COMMENT '投递租约失效时间戳毫秒',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    last_error VARCHAR(1000) NULL COMMENT '最近一次投递失败原因',
    create_time BIGINT NOT NULL COMMENT '创建时间戳毫秒',
    update_time BIGINT NOT NULL COMMENT '状态更新时间戳毫秒',
    PRIMARY KEY (id),
    KEY idx_infra_schedule_outbox_pending (status, claim_until, id),
    KEY idx_infra_schedule_outbox_job (job_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可靠任务触发 Outbox 表';
