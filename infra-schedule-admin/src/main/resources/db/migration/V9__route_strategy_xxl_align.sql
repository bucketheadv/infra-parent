-- 路由策略枚举对齐 xxl-job：ROUND_ROBIN -> ROUND，BROADCAST -> SHARDING_BROADCAST
UPDATE infra_schedule_job
SET route_strategy = 'ROUND'
WHERE route_strategy = 'ROUND_ROBIN';

UPDATE infra_schedule_job
SET route_strategy = 'SHARDING_BROADCAST'
WHERE route_strategy = 'BROADCAST';
