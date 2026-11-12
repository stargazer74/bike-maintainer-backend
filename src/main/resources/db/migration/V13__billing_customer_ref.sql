-- Polar subscription (backend#139): the payment provider only learns a random reference per account, never the
-- internal id. Created on the first checkout; null for accounts that never started one.
ALTER TABLE app_user ADD COLUMN billing_customer_ref VARCHAR(36) NULL;
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_billing_customer_ref UNIQUE (billing_customer_ref);
