ALTER TABLE reward_component
    ADD COLUMN direct_prize_mount TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否将奖品直接挂载到奖励模板，1是0否' AFTER definition_json;
