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
VALUES (1, 'FIXED', 'fixed_prize', '固定奖品', '系统预置的固定奖品字段：类型、ID、名称、图标、价值、展示价值和数量', JSON_OBJECT('nodes', JSON_ARRAY()), 1, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)
ON DUPLICATE KEY UPDATE
    type = 'FIXED',
    code = 'fixed_prize',
    name = '固定奖品',
    description = '系统预置的固定奖品字段：类型、ID、名称、图标、价值、展示价值和数量',
    definition_json = JSON_OBJECT('nodes', JSON_ARRAY()),
    enabled = 1;

ALTER TABLE reward_component_prize
    ADD COLUMN prize_component_id BIGINT NOT NULL DEFAULT 1 COMMENT '采用的奖品组件主键，默认固定奖品组件 1' AFTER component_id,
    ADD KEY idx_reward_component_prize_prize_component (prize_component_id),
    ADD CONSTRAINT fk_reward_component_prize_prize_component FOREIGN KEY (prize_component_id) REFERENCES prize_component (id);

UPDATE reward_component_prize
SET prize_component_id = 1
WHERE prize_component_id IS NULL;
