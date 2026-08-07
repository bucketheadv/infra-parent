INSERT INTO activity_component (code, name, description, definition_json, enabled, create_time, update_time)
VALUES (
    'basic_info',
    '基础信息',
    '活动名称、日期、日期时间和类型',
    JSON_OBJECT(
        'nodes', JSON_ARRAY(
            JSON_OBJECT('key', 'title', 'label', '活动标题', 'type', 'TEXT', 'required', true, 'placeholder', '请输入活动标题', 'options', JSON_ARRAY(), 'children', JSON_ARRAY()),
            JSON_OBJECT('key', 'event_date', 'label', '活动日期', 'type', 'DATE', 'required', true, 'options', JSON_ARRAY(), 'children', JSON_ARRAY()),
            JSON_OBJECT('key', 'event_time', 'label', '活动开始时间', 'type', 'DATE_TIME', 'required', true, 'defaultValue', '2026-08-08T09:30:00', 'options', JSON_ARRAY(), 'children', JSON_ARRAY()),
            JSON_OBJECT('key', 'category', 'label', '活动类型', 'type', 'SELECT', 'required', true, 'defaultValue', 'online', 'options', JSON_ARRAY(JSON_OBJECT('value', 'online', 'label', '线上活动'), JSON_OBJECT('value', 'offline', 'label', '线下活动')), 'children', JSON_ARRAY())
        )
    ),
    1,
    UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000,
    UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), definition_json = VALUES(definition_json), update_time = VALUES(update_time);

INSERT INTO activity_template (code, name, description, definition_json, enabled, create_time, update_time)
VALUES (
    'basic_campaign',
    '基础活动模板',
    '引用基础信息组件的演示模板',
    JSON_OBJECT('nodes', JSON_ARRAY()),
    1,
    UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000,
    UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), definition_json = VALUES(definition_json), update_time = VALUES(update_time);

INSERT INTO activity_template_component (template_id, component_id, mount_key, mount_title, mount_mode, sort_no, required)
SELECT template.id, component.id, 'basic_info', '基础信息', 'SINGLE', 1, 0
FROM activity_template template
JOIN activity_component component ON component.code = 'basic_info'
WHERE template.code = 'basic_campaign'
ON DUPLICATE KEY UPDATE mount_key = VALUES(mount_key), mount_title = VALUES(mount_title), mount_mode = VALUES(mount_mode), sort_no = VALUES(sort_no), required = VALUES(required);
