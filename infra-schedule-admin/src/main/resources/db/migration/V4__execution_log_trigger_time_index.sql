-- 支持按触发时间范围全局检索执行日志。
ALTER TABLE infra_schedule_execution_log
    ADD KEY idx_infra_schedule_log_trigger (trigger_time);
