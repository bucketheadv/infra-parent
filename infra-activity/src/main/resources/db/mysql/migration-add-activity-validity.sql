ALTER TABLE activity
    ADD COLUMN valid_forever TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否永久有效，1是0否' AFTER status,
    ADD COLUMN valid_start_time BIGINT NULL COMMENT '非永久活动开始时间戳，单位毫秒' AFTER valid_forever,
    ADD COLUMN valid_end_time BIGINT NULL COMMENT '非永久活动结束时间戳，单位毫秒' AFTER valid_start_time;
