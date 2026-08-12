-- 支持按状态+触发时间回收长时间未结束的运行中日志。
ALTER TABLE infra_schedule_execution_log
    ADD KEY idx_infra_schedule_log_status_trigger (status, trigger_time);
