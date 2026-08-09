CREATE TABLE activity_task_definition (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务模板主键',
    code VARCHAR(64) NOT NULL COMMENT '任务模板唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '任务模板展示名称',
    handler_type VARCHAR(64) NOT NULL COMMENT '后端任务处理器类型',
    description VARCHAR(512) NULL COMMENT '任务模板用途说明',
    default_parameters_json JSON NOT NULL COMMENT '任务默认执行参数 JSON',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '任务最大重试次数',
    retry_interval_millis BIGINT NOT NULL DEFAULT 60000 COMMENT '任务重试间隔，单位毫秒',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许新活动模板关联，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_task_definition_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动可复用任务定义表';

CREATE TABLE activity_template_task_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动模板任务关联主键',
    activity_template_id BIGINT NOT NULL COMMENT '所属活动模板主键',
    task_template_id BIGINT NOT NULL COMMENT '关联任务模板主键',
    code VARCHAR(64) NOT NULL COMMENT '当前活动模板内唯一任务编码',
    name VARCHAR(128) NOT NULL COMMENT '当前活动模板中的任务展示名称',
    handler_type VARCHAR(64) NOT NULL COMMENT '任务处理器类型快照',
    trigger_type VARCHAR(32) NOT NULL COMMENT '任务触发方式',
    trigger_config_json JSON NOT NULL COMMENT '任务触发配置 JSON',
    parameter_overrides_json JSON NOT NULL COMMENT '活动专属任务参数覆盖 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用该任务关联，1是0否',
    sort_no INT NOT NULL COMMENT '任务执行排序',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_template_task_binding_code (activity_template_id, code),
    KEY idx_activity_template_task_binding_template (activity_template_id),
    KEY idx_activity_template_task_binding_definition (task_template_id),
    CONSTRAINT fk_activity_template_task_binding_template
        FOREIGN KEY (activity_template_id) REFERENCES activity_template (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_activity_template_task_binding_definition
        FOREIGN KEY (task_template_id) REFERENCES activity_task_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板任务绑定表';

CREATE TABLE activity_task_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动任务实例主键',
    activity_id BIGINT NOT NULL COMMENT '所属活动主键',
    activity_template_task_id BIGINT NOT NULL COMMENT '来源活动模板任务关联主键',
    task_template_id BIGINT NOT NULL COMMENT '来源任务模板主键',
    code VARCHAR(64) NOT NULL COMMENT '活动内唯一任务编码',
    name VARCHAR(128) NOT NULL COMMENT '任务展示名称',
    handler_type VARCHAR(64) NOT NULL COMMENT '后端任务处理器类型',
    trigger_type VARCHAR(32) NOT NULL COMMENT '任务触发方式',
    trigger_config_json JSON NOT NULL COMMENT '任务触发配置快照 JSON',
    parameters_json JSON NOT NULL COMMENT '合并后的任务参数快照 JSON',
    max_retry_count INT NOT NULL COMMENT '最大重试次数快照',
    retry_interval_millis BIGINT NOT NULL COMMENT '重试间隔快照，单位毫秒',
    next_trigger_time BIGINT NULL COMMENT '下一次触发时间戳，单位毫秒',
    status VARCHAR(16) NOT NULL COMMENT '调度状态',
    lease_owner VARCHAR(128) NULL COMMENT '当前分布式租约持有实例',
    lease_expire_time BIGINT NULL COMMENT '当前分布式租约失效时间戳，单位毫秒',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    last_trigger_time BIGINT NULL COMMENT '最近一次实际触发时间戳，单位毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_task_instance_code (activity_id, code),
    KEY idx_activity_task_instance_due (status, next_trigger_time),
    KEY idx_activity_task_instance_activity (activity_id),
    CONSTRAINT fk_activity_task_instance_activity
        FOREIGN KEY (activity_id) REFERENCES activity (id)
        ON DELETE CASCADE,
    KEY idx_activity_task_instance_binding (activity_template_task_id),
    CONSTRAINT fk_activity_task_instance_definition
        FOREIGN KEY (task_template_id) REFERENCES activity_task_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动上线后生成的任务实例表';

CREATE TABLE activity_task_execution_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务执行记录主键',
    activity_task_id BIGINT NOT NULL COMMENT '所属活动任务实例主键',
    execution_key VARCHAR(160) NOT NULL COMMENT '幂等执行键',
    trigger_source VARCHAR(16) NOT NULL COMMENT '触发来源，SCHEDULED、MANUAL 或 RETRY',
    trigger_time BIGINT NOT NULL COMMENT '本次计划触发时间戳，单位毫秒',
    status VARCHAR(16) NOT NULL COMMENT '执行状态',
    attempt_no INT NOT NULL COMMENT '当前执行尝试次数',
    request_json JSON NOT NULL COMMENT '手动触发说明或执行上下文 JSON',
    result_json JSON NULL COMMENT '执行结果 JSON',
    error_message VARCHAR(1024) NULL COMMENT '失败原因',
    start_time BIGINT NULL COMMENT '开始时间戳，单位毫秒',
    end_time BIGINT NULL COMMENT '结束时间戳，单位毫秒',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_task_execution_log_key (execution_key),
    KEY idx_activity_task_execution_log_instance (activity_task_id, create_time),
    CONSTRAINT fk_activity_task_execution_log_instance
        FOREIGN KEY (activity_task_id) REFERENCES activity_task_instance (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务执行日志与幂等记录表';
