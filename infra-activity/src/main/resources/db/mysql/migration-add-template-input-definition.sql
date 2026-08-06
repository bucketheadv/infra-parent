ALTER TABLE activity_template
    ADD COLUMN definition_json JSON NULL COMMENT '模板直接挂载的普通输入项定义 JSON' AFTER description;
