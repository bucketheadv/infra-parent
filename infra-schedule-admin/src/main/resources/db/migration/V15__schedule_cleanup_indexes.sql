-- 历史清理按时间窗口、小批量执行；补齐筛选与排序索引。
ALTER TABLE infra_schedule_execution_log
    ADD KEY idx_infra_schedule_log_cleanup (finish_time, id);

ALTER TABLE infra_schedule_trigger_outbox
    ADD KEY idx_infra_schedule_outbox_cleanup (status, update_time, id);
