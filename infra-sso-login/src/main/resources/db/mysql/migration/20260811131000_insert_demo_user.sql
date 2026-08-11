-- 仅用于本地开发环境的演示账户：demo / demo。
INSERT INTO sso_user (username, email, password_hash, enabled)
VALUES (
    'demo',
    'demo@example.com',
    '{bcrypt}$2y$10$XJ4uP/Ok0lQ6GHQ/OWTTJuGgg6GCOO3lTNU9oylLzD.w8loFhPhFO',
    1
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id),
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    enabled = VALUES(enabled);

INSERT INTO sso_user_role (user_id, role_code)
VALUES (LAST_INSERT_ID(), 'ORDER_READ')
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code);
