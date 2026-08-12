-- infra-schedule 最终表结构（新建库直接执行本文件；已有库请按 db/migration 增量升级）

CREATE TABLE IF NOT EXISTS infra_schedule_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键',
    name VARCHAR(128) NOT NULL COMMENT '管理端展示名称',
    executor_id BIGINT NOT NULL COMMENT '指定执行器表主键',
    handler VARCHAR(128) NOT NULL COMMENT '任务处理器名称',
    parameters TEXT NOT NULL COMMENT '传递给处理器的参数',
    schedule_type VARCHAR(16) NOT NULL COMMENT '调度类型：CRON / FIXED_RATE',
    cron VARCHAR(128) NULL COMMENT 'Cron 表达式',
    fixed_rate_millis BIGINT NULL COMMENT '固定间隔毫秒',
    status VARCHAR(16) NOT NULL COMMENT '任务状态：ENABLED / DISABLED',
    route_strategy VARCHAR(32) NOT NULL COMMENT '路由策略',
    block_strategy VARCHAR(32) NOT NULL COMMENT '阻塞策略',
    resident TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否常驻任务；常驻时串行跳过/丢弃后续不写调度日志',
    max_retry_count INT NOT NULL DEFAULT 0 COMMENT '最大额外重试次数',
    retry_interval_millis BIGINT NOT NULL DEFAULT 1000 COMMENT '重试间隔毫秒',
    timeout_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '单次执行超时秒数，0 表示不限制',
    next_trigger_at BIGINT NULL COMMENT '下次触发时间戳毫秒',
    last_trigger_at BIGINT NULL COMMENT '最近一次定时触发时间戳毫秒',
    claim_owner VARCHAR(128) NULL COMMENT '当前调度租约持有节点',
    claim_until BIGINT NULL COMMENT '租约失效时间戳毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳毫秒',
    PRIMARY KEY (id),
    KEY idx_infra_schedule_job_due (status, next_trigger_at, claim_until),
    KEY idx_infra_schedule_job_executor (executor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务定义表';

CREATE TABLE IF NOT EXISTS infra_schedule_execution_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行日志主键',
    job_id BIGINT NOT NULL COMMENT '所属任务主键',
    executor_id BIGINT NULL COMMENT '实际处理的执行器表主键',
    trigger_time BIGINT NOT NULL COMMENT '调度触发时间戳毫秒',
    finish_time BIGINT NULL COMMENT '处理结束时间戳毫秒',
    status VARCHAR(16) NOT NULL COMMENT '执行状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已执行重试次数',
    message TEXT NULL COMMENT '结果、错误或跳过原因',
    handle_log MEDIUMTEXT NULL COMMENT '业务执行过程日志（执行器异步上报）',
    target_address VARCHAR(512) NULL COMMENT '本次调用目标地址',
    duration_millis BIGINT NULL COMMENT '实际执行耗时毫秒',
    PRIMARY KEY (id),
    KEY idx_infra_schedule_log_job (job_id, trigger_time),
    KEY idx_infra_schedule_log_trigger (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行审计日志表';

CREATE TABLE IF NOT EXISTS infra_schedule_executor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行器主键',
    executor_group VARCHAR(128) NOT NULL COMMENT '执行器分组标识，全局唯一',
    executor_name VARCHAR(128) NOT NULL COMMENT '管理端展示名称',
    address TEXT NULL COMMENT '地址列表；手动模式为多地址，自动模式由注册表同步',
    address_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_REGISTER' COMMENT '地址模式：AUTO_REGISTER / MANUAL',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '执行器状态：ENABLED / DISABLED',
    last_heartbeat_time BIGINT NOT NULL COMMENT '最近心跳时间戳毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_schedule_executor_group (executor_group),
    KEY idx_infra_schedule_executor_heartbeat (status, last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器分组表';

CREATE TABLE IF NOT EXISTS infra_schedule_executor_registry (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '注册实例主键',
    executor_id BIGINT NOT NULL COMMENT '所属执行器分组主键',
    address VARCHAR(512) NOT NULL COMMENT '实例访问地址',
    last_heartbeat_time BIGINT NOT NULL COMMENT '该地址最近心跳时间戳毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_schedule_executor_registry (executor_id, address),
    KEY idx_infra_schedule_registry_alive (executor_id, last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动注册模式下的执行器实例地址表';
