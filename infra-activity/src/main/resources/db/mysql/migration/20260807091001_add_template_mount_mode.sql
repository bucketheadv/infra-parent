ALTER TABLE activity_template_component
    ADD COLUMN mount_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '挂载形式，SINGLE 或 ARRAY' AFTER mount_key;
