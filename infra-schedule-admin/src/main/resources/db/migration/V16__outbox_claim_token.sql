-- 每次 Outbox 领取都生成唯一 token，过期的旧工作线程不能续租、完成或释放新一轮租约。
ALTER TABLE infra_schedule_trigger_outbox
    ADD COLUMN claim_token CHAR(36) NULL COMMENT '本次投递领取唯一令牌' AFTER claim_owner;
