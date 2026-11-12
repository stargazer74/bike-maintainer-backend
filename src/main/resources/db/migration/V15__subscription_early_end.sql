-- Early end of a subscription cancelled after its first year (backend#147): it ends one month after the cancellation
-- and the prepaid rest is refunded pro rata (§ 309 Nr. 9 BGB). The paid period is kept from the time of the
-- cancellation (Polar changes it once the subscription is revoked); the order id and the amount already refunded
-- before our refund make a retry safe against refunding twice.
ALTER TABLE subscription ADD COLUMN early_end_received_on DATE NULL;
ALTER TABLE subscription ADD COLUMN early_end_on DATE NULL;
ALTER TABLE subscription ADD COLUMN early_end_period_start DATE NULL;
ALTER TABLE subscription ADD COLUMN early_end_period_end DATE NULL;
ALTER TABLE subscription ADD COLUMN early_end_state VARCHAR(20) NULL;
ALTER TABLE subscription ADD COLUMN early_end_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE subscription ADD COLUMN early_end_order_id VARCHAR(100) NULL;
ALTER TABLE subscription ADD COLUMN early_end_refunded_before BIGINT NULL;

CREATE INDEX idx_subscription_early_end_state ON subscription (early_end_state);
