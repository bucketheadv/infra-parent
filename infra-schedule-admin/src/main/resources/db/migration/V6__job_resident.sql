-- 任务是否常驻：常驻任务在串行跳过 / 丢弃后续时不写调度日志。
ALTER TABLE infra_schedule_job
    ADD COLUMN resident TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否常驻任务' AFTER block_strategy;
