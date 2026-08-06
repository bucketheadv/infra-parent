CREATE TABLE sso_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(128) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sso_user_username (username),
    UNIQUE KEY uk_sso_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sso_user_role (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(128) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_sso_user_role_user
        FOREIGN KEY (user_id) REFERENCES sso_user (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
