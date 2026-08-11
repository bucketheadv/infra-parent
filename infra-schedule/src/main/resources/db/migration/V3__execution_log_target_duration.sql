ALTER TABLE infra_schedule_execution_log
    ADD COLUMN target_address VARCHAR(512) NULL,
    ADD COLUMN duration_millis BIGINT NULL;
