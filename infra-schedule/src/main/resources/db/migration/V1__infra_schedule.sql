CREATE TABLE IF NOT EXISTS infra_schedule_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    executor_id BIGINT NOT NULL,
    handler VARCHAR(128) NOT NULL,
    parameters TEXT NOT NULL,
    schedule_type VARCHAR(16) NOT NULL,
    cron VARCHAR(128) NULL,
    fixed_rate_millis BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    route_strategy VARCHAR(32) NOT NULL,
    block_strategy VARCHAR(32) NOT NULL,
    max_retry_count INT NOT NULL DEFAULT 0,
    retry_interval_millis BIGINT NOT NULL DEFAULT 1000,
    timeout_seconds BIGINT NOT NULL DEFAULT 0,
    next_trigger_at BIGINT NULL,
    last_trigger_at BIGINT NULL,
    claim_owner VARCHAR(128) NULL,
    claim_until BIGINT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_infra_schedule_job_due (status, next_trigger_at, claim_until),
    KEY idx_infra_schedule_job_executor (executor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS infra_schedule_execution_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    executor_id BIGINT NULL,
    trigger_time BIGINT NOT NULL,
    finish_time BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    message TEXT NULL,
    target_address VARCHAR(512) NULL,
    duration_millis BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_infra_schedule_log_job (job_id, trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS infra_schedule_executor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    executor_group VARCHAR(128) NOT NULL,
    executor_name VARCHAR(128) NOT NULL,
    address VARCHAR(512) NULL,
    address_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_REGISTER',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    last_heartbeat_time BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_schedule_executor_group (executor_group),
    KEY idx_infra_schedule_executor_heartbeat (status, last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
