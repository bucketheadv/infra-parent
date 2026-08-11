-- 执行日志改为记录执行器表主键 ID（BIGINT）；清理无法转换为数字的历史值。
UPDATE infra_schedule_execution_log
SET executor_id = NULL
WHERE executor_id IS NOT NULL
  AND executor_id NOT REGEXP '^[0-9]+$';

ALTER TABLE infra_schedule_execution_log
    MODIFY COLUMN executor_id BIGINT NULL;
