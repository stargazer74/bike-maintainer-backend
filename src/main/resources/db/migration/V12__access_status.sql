-- Access status (backend#137): portal accounts get a free trial from the day their email address is verified,
-- afterwards they need a paid subscription to write. Household mode ignores both.
ALTER TABLE app_user ADD COLUMN trial_started_on DATE NULL;

-- Accounts that exist already start their trial with this release (decided in backend#136). Household profiles have
-- no email address and are left alone.
UPDATE app_user
SET trial_started_on = CURRENT_DATE
WHERE email IS NOT NULL
  AND email_verified = TRUE;

-- Subscriptions with a payment provider; kept independent of the provider so another one can be added later.
-- Only the provider's subscription id, its state and the paid period are stored, no payment data.
CREATE TABLE subscription
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    paid_until  DATE         NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subscription_provider_external UNIQUE (provider, external_id),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_user_id ON subscription (user_id);
