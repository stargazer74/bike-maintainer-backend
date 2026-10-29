-- One-time tokens sent by email (email verification, later password reset). Only the SHA-256 hash of a token
-- is stored, so a leaked database does not contain usable links.
CREATE TABLE user_token
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(30) NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP   NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_token_user_type ON user_token (user_id, type);

-- Emails sent per day (all kinds together), for the daily limit. Stored so that a restart does not reset it.
CREATE TABLE mail_quota
(
    quota_date DATE NOT NULL PRIMARY KEY,
    sent_count INT  NOT NULL
);
