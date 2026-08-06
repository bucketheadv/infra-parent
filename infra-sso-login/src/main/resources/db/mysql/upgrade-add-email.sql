-- 已执行旧版 schema.sql 的数据库需要在部署新版本前执行本脚本。
ALTER TABLE sso_user
    ADD COLUMN email VARCHAR(255) NULL AFTER username;

-- 旧账户没有邮箱时使用不会投递的唯一占位地址，部署后应由账户管理功能维护真实邮箱。
UPDATE sso_user
SET email = CONCAT(username, '@example.invalid')
WHERE email IS NULL OR email = '';

ALTER TABLE sso_user
    MODIFY COLUMN email VARCHAR(255) NOT NULL,
    ADD UNIQUE KEY uk_sso_user_email (email);
