CREATE TABLE activity_component (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动组件主键',
    code VARCHAR(64) NOT NULL COMMENT '组件唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '组件展示名称',
    description VARCHAR(512) NULL COMMENT '组件用途说明',
    definition_json JSON NOT NULL COMMENT '递归字段定义 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许新模板引用，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_component_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动可复用组件表';

CREATE TABLE activity_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动模板主键',
    code VARCHAR(64) NOT NULL COMMENT '模板唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '模板展示名称',
    description VARCHAR(512) NULL COMMENT '模板用途说明',
    definition_json JSON NOT NULL COMMENT '模板直接挂载的普通输入项定义 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许用于创建活动，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_template_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板表';

CREATE TABLE activity_template_component (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模板组件挂载记录主键',
    template_id BIGINT NOT NULL COMMENT '所属活动模板主键',
    component_id BIGINT NOT NULL COMMENT '被引用活动组件主键',
    mount_key VARCHAR(64) NOT NULL COMMENT '模板内唯一挂载键，也是活动配置数据根路径',
    mount_title VARCHAR(128) NOT NULL COMMENT '活动表单中展示的组件挂载标题',
    mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '挂载形式，SINGLE 或 ARRAY',
    sort_no INT NOT NULL COMMENT '组件在模板中的展示顺序',
    required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求填写组件内容，1是0否',
    overrides_json JSON NULL COMMENT '组件展示覆盖配置 JSON',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_template_component_sort (template_id, sort_no),
    UNIQUE KEY uk_activity_template_component_mount_key (template_id, mount_key),
    KEY idx_activity_template_component_template (template_id),
    CONSTRAINT fk_activity_template_component_template
        FOREIGN KEY (template_id) REFERENCES activity_template (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_activity_template_component_component
        FOREIGN KEY (component_id) REFERENCES activity_component (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板组件挂载表';

CREATE TABLE reward_component (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖励组件主键',
    code VARCHAR(64) NOT NULL COMMENT '奖励组件唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '奖励组件展示名称',
    description VARCHAR(512) NULL COMMENT '奖励组件用途说明',
    definition_json JSON NOT NULL COMMENT '奖励组件输入字段定义 JSON',
    direct_prize_mount TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否将奖品直接挂载到奖励模板，1是0否',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许新奖励模板引用，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_component_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动奖励可复用组件表';

CREATE TABLE reward_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖励模板主键',
    code VARCHAR(64) NOT NULL COMMENT '奖励模板唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '奖励模板展示名称',
    description VARCHAR(512) NULL COMMENT '奖励模板用途说明',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许活动模板引用，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_template_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动奖励模板表';

CREATE TABLE reward_template_component (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖励模板组件挂载记录主键',
    template_id BIGINT NOT NULL COMMENT '所属奖励模板主键',
    component_id BIGINT NOT NULL COMMENT '被引用奖励组件主键',
    mount_key VARCHAR(64) NOT NULL COMMENT '奖励模板内唯一挂载键',
    mount_title VARCHAR(128) NOT NULL COMMENT '配置页面中的组件挂载标题',
    mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '奖励组件挂载形式，SINGLE 或 ARRAY',
    sort_no INT NOT NULL COMMENT '组件展示顺序',
    required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求填写组件内容，1是0否',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_template_component_mount_key (template_id, mount_key),
    KEY idx_reward_template_component_template (template_id),
    CONSTRAINT fk_reward_template_component_template
        FOREIGN KEY (template_id) REFERENCES reward_template (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reward_template_component_component
        FOREIGN KEY (component_id) REFERENCES reward_component (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励模板奖励组件挂载表';

CREATE TABLE prize_component (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖品组件主键；固定奖品组件固定为 1',
    type VARCHAR(16) NOT NULL COMMENT '奖品组件类型，FIXED 或 EXTENSION',
    code VARCHAR(64) NOT NULL COMMENT '奖品组件唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '奖品组件展示名称',
    description VARCHAR(512) NULL COMMENT '奖品组件用途说明',
    definition_json JSON NOT NULL COMMENT '固定奖品字段之外的扩展字段定义 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许新奖励组件挂载，1是0否',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prize_component_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖品组件定义表';

INSERT INTO prize_component (id, type, code, name, description, definition_json, enabled, create_time, update_time)
VALUES (1, 'FIXED', 'fixed_prize', '固定奖品', '系统预置的固定奖品字段：类型、ID、名称、图标、价值、展示价值和数量', JSON_OBJECT('nodes', JSON_ARRAY()), 1, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000);

CREATE TABLE reward_component_prize (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖励组件奖品组件挂载记录主键',
    component_id BIGINT NOT NULL COMMENT '所属奖励组件主键',
    prize_component_id BIGINT NOT NULL DEFAULT 1 COMMENT '采用的奖品组件主键，默认固定奖品组件 1',
    mount_key VARCHAR(64) NOT NULL COMMENT '奖励组件内唯一奖品挂载键',
    mount_title VARCHAR(128) NOT NULL COMMENT '配置页面中的奖品挂载标题',
    mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '奖品组件挂载形式，SINGLE 或 ARRAY',
    array_size INT NULL COMMENT '奖品数组固定长度，为空表示可自由增删',
    sort_no INT NOT NULL COMMENT '奖品展示顺序',
    required TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否要求填写奖品内容，1是0否',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_component_prize_mount_key (component_id, mount_key),
    KEY idx_reward_component_prize_component (component_id),
    KEY idx_reward_component_prize_prize_component (prize_component_id),
    CONSTRAINT fk_reward_component_prize_component
        FOREIGN KEY (component_id) REFERENCES reward_component (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reward_component_prize_prize_component
        FOREIGN KEY (prize_component_id) REFERENCES prize_component (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励组件奖品组件挂载表';

CREATE TABLE activity_template_reward_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动模板奖励模板挂载记录主键',
    template_id BIGINT NOT NULL COMMENT '所属活动模板主键',
    reward_template_id BIGINT NOT NULL COMMENT '被引用奖励模板主键',
    mount_key VARCHAR(64) NOT NULL COMMENT '活动模板内唯一奖励挂载键',
    mount_title VARCHAR(128) NOT NULL COMMENT '活动配置页面中的奖励挂载标题',
    sort_no INT NOT NULL COMMENT '奖励模板展示顺序',
    required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求填写整个奖励模板，1是0否',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_template_reward_template_mount_key (template_id, mount_key),
    KEY idx_activity_template_reward_template_template (template_id),
    CONSTRAINT fk_activity_template_reward_template_template
        FOREIGN KEY (template_id) REFERENCES activity_template (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_activity_template_reward_template_reward_template
        FOREIGN KEY (reward_template_id) REFERENCES reward_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板奖励模板挂载表';

CREATE TABLE activity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动配置主键',
    name VARCHAR(128) NOT NULL COMMENT '活动展示名称',
    template_id BIGINT NOT NULL COMMENT '采用的活动模板主键',
    status VARCHAR(16) NOT NULL COMMENT '活动状态，DRAFT 或 ACTIVE',
    online_status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '上下线状态，ONLINE 或 OFFLINE',
    valid_forever TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否永久有效，1是0否',
    valid_start_time BIGINT NULL COMMENT '非永久活动开始时间戳，单位毫秒',
    valid_end_time BIGINT NULL COMMENT '非永久活动结束时间戳，单位毫秒',
    debug_mode TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用仅面向白名单用户的调试模式，1是0否',
    debug_user_ids_json JSON NOT NULL COMMENT '调试模式允许访问的用户主键 JSON 数组',
    debug_force_time BIGINT NULL COMMENT '调试模式强制使用的时间戳，单位毫秒',
    form_data_json JSON NOT NULL COMMENT '按模板层级保存的活动配置 JSON',
    create_time BIGINT NOT NULL COMMENT '创建时间戳，单位毫秒',
    update_time BIGINT NOT NULL COMMENT '更新时间戳，单位毫秒',
    PRIMARY KEY (id),
    KEY idx_activity_template (template_id),
    CONSTRAINT fk_activity_template
        FOREIGN KEY (template_id) REFERENCES activity_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动配置表';

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
    CONSTRAINT fk_activity_template_task_binding_template FOREIGN KEY (activity_template_id) REFERENCES activity_template (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_template_task_binding_definition FOREIGN KEY (task_template_id) REFERENCES activity_task_definition (id)
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
    KEY idx_activity_task_instance_binding (activity_template_task_id),
    CONSTRAINT fk_activity_task_instance_activity FOREIGN KEY (activity_id) REFERENCES activity (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_task_instance_definition FOREIGN KEY (task_template_id) REFERENCES activity_task_definition (id)
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
    CONSTRAINT fk_activity_task_execution_log_instance FOREIGN KEY (activity_task_id) REFERENCES activity_task_instance (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务执行日志与幂等记录表';
