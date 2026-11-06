-- Devices (apps) that stay logged in: the token is exchanged for a session when the session expired. Only SHA-256
-- hashes are stored; the previous hash detects a copied token that is used again. No IP addresses or locations.
CREATE TABLE device_login
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    name                VARCHAR(100) NOT NULL,
    token_hash          CHAR(64)     NOT NULL,
    previous_token_hash CHAR(64)     NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_login_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_device_login_previous_token_hash UNIQUE (previous_token_hash),
    CONSTRAINT fk_device_login_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_device_login_user ON device_login (user_id);
