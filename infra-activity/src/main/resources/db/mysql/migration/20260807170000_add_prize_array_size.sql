ALTER TABLE reward_component_prize
    ADD COLUMN array_size INT NULL COMMENT '奖品数组固定长度，为空表示可自由增删' AFTER mount_mode;
