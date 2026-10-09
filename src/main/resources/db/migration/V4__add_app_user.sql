CREATE TABLE app_user
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100),
    role                  VARCHAR(20)  NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP    NULL,
    language              VARCHAR(5)   NOT NULL DEFAULT 'de',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

-- Initial admin that owns all vehicles created before user accounts existed. The password is left
-- empty here (it cannot be hashed in SQL) and is set once on startup from BM_INITIAL_ADMIN_PASSWORD.
INSERT INTO app_user (email, role, active, email_verified, language)
VALUES (LOWER(TRIM('${initialAdminEmail}')), 'ADMIN', TRUE, TRUE, 'de');

ALTER TABLE vehicle
    ADD COLUMN user_id BIGINT NULL;

UPDATE vehicle
SET user_id = (SELECT id FROM app_user WHERE role = 'ADMIN')
WHERE user_id IS NULL;

ALTER TABLE vehicle
    MODIFY user_id BIGINT NOT NULL;

ALTER TABLE vehicle
    ADD CONSTRAINT fk_vehicle_user FOREIGN KEY (user_id) REFERENCES app_user (id);

CREATE INDEX idx_vehicle_user_id ON vehicle (user_id);
