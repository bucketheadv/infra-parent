-- 暂停仅停止定时扫描；管理员立即执行的 Outbox 仍按同一租约和重试链路投递。
ALTER TABLE infra_schedule_trigger_outbox
    ADD COLUMN manual_trigger TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员立即执行触发' AFTER trigger_time;
