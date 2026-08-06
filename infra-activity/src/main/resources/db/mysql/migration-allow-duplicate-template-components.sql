ALTER TABLE activity_template_component
    DROP INDEX uk_activity_template_component,
    ADD COLUMN mount_key VARCHAR(64) NULL COMMENT '模板内唯一挂载键，也是活动配置数据根路径' AFTER component_id,
    ADD COLUMN mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '挂载形式，SINGLE 或 ARRAY' AFTER mount_key,
    ADD UNIQUE KEY uk_activity_template_component_sort (template_id, sort_no);

UPDATE activity_template_component
SET mount_key = CONCAT('component_', id)
WHERE mount_key IS NULL;

ALTER TABLE activity_template_component
    MODIFY COLUMN mount_key VARCHAR(64) NOT NULL COMMENT '模板内唯一挂载键，也是活动配置数据根路径',
    ADD UNIQUE KEY uk_activity_template_component_mount_key (template_id, mount_key);

ALTER TABLE activity_template
    ADD COLUMN definition_json JSON NULL COMMENT '模板直接挂载的普通输入项定义 JSON' AFTER description;
