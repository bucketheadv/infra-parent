-- 业务执行日志（类似 XxlJobHelper.log），由执行器异步上报，与终态 message 分离。
ALTER TABLE infra_schedule_execution_log
    ADD COLUMN handle_log MEDIUMTEXT NULL COMMENT '业务执行过程日志' AFTER message;
