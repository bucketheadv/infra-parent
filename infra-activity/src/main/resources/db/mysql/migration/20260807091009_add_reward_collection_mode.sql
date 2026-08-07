ALTER TABLE reward_template_component
    ADD COLUMN mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '奖励组件挂载形式，SINGLE 或 ARRAY' AFTER mount_title;

ALTER TABLE reward_component_prize
    ADD COLUMN mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '奖品组件挂载形式，SINGLE 或 ARRAY' AFTER mount_title;
