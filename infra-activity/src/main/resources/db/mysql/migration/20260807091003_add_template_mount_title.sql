ALTER TABLE activity_template_component
    ADD COLUMN mount_title VARCHAR(128) NULL COMMENT '活动表单中展示的组件挂载标题' AFTER mount_key;

UPDATE activity_template_component binding
JOIN activity_component component ON component.id = binding.component_id
SET binding.mount_title = component.name
WHERE binding.mount_title IS NULL OR TRIM(binding.mount_title) = '';

ALTER TABLE activity_template_component
    MODIFY COLUMN mount_title VARCHAR(128) NOT NULL COMMENT '活动表单中展示的组件挂载标题';
