ALTER TABLE activity
    ADD COLUMN debug_mode TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用仅面向白名单用户的调试模式，1是0否' AFTER valid_end_time,
    ADD COLUMN debug_user_ids_json JSON NULL COMMENT '调试模式允许访问的用户主键 JSON 数组' AFTER debug_mode,
    ADD COLUMN debug_force_time BIGINT NULL COMMENT '调试模式强制使用的时间戳，单位毫秒' AFTER debug_user_ids_json;

UPDATE activity
SET debug_user_ids_json = JSON_ARRAY()
WHERE debug_user_ids_json IS NULL;

ALTER TABLE activity
    MODIFY COLUMN debug_user_ids_json JSON NOT NULL COMMENT '调试模式允许访问的用户主键 JSON 数组';
