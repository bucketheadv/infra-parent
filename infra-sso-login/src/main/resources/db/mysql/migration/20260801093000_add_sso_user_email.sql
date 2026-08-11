-- 已执行早期 SSO 用户表结构的数据库需执行本迁移。
ALTER TABLE sso_user
    ADD COLUMN email VARCHAR(255) NULL COMMENT '用户邮箱' AFTER username;

-- 旧账户没有邮箱时使用不会投递的唯一占位地址，后续应由账户管理功能维护真实邮箱。
UPDATE sso_user
SET email = CONCAT(username, '@example.invalid')
WHERE email IS NULL OR email = '';

ALTER TABLE sso_user
    MODIFY COLUMN email VARCHAR(255) NOT NULL COMMENT '用户邮箱',
    ADD UNIQUE KEY UK_SSO_USER_EMAIL (email);
