ALTER TABLE activity_task_scheduler_instance
    ADD COLUMN node_ip VARCHAR(64) NOT NULL DEFAULT '' COMMENT '调度节点 IP 地址' AFTER instance_id;
