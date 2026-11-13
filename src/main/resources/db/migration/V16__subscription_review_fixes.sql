-- Fixes of the review of the subscription epic (backend v0.18.0).

-- Optimistic locking: the webhook, the nightly early end and the nightly re-sync change the same row; a save based
-- on an outdated copy now fails and is repeated on the current row instead of overwriting the other change.
ALTER TABLE subscription ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Last time the payment provider confirmed the state of the subscription. A running subscription whose paid period
-- is over only grants access while it is confirmed, so a lost webhook cannot grant access forever.
ALTER TABLE subscription ADD COLUMN synced_at TIMESTAMP NULL;
UPDATE subscription SET synced_at = updated_at;

-- Accounts verified by a password reset never got their trial (backend v0.18.0) and were read-only from the start;
-- they start it now, like the existing accounts did with V12. Household profiles have no email address.
UPDATE app_user
SET trial_started_on = CURRENT_DATE
WHERE email IS NOT NULL
  AND email_verified = TRUE
  AND trial_started_on IS NULL;
