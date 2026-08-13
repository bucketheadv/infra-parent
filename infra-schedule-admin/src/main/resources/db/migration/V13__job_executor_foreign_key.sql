-- 历史版本没有任务到执行器的外键。升级前先停用历史孤儿任务，避免直接加约束导致整次迁移失败。
-- 这些任务无法被正常路由，保留定义供管理员修正并重新启用，而不是删除审计数据。
UPDATE infra_schedule_job AS job
LEFT JOIN infra_schedule_executor AS executor ON executor.id = job.executor_id
SET job.status = 'DISABLED',
    job.next_trigger_at = NULL,
    job.claim_owner = NULL,
    job.claim_until = NULL,
    job.update_time = CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)
WHERE executor.id IS NULL;

-- 任务必须绑定真实存在的执行器；ON DELETE RESTRICT 同时消除删除执行器与新建任务并发时的孤儿引用。
ALTER TABLE infra_schedule_job
    ADD CONSTRAINT fk_infra_schedule_job_executor
        FOREIGN KEY (executor_id) REFERENCES infra_schedule_executor (id)
        ON DELETE RESTRICT;
