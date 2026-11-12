-- Retention of expired accounts (backend#140): an account whose access (trial or paid period) ended 12 months ago is
-- deleted, after a notice by email 30 days before. Login times are deliberately not recorded: only when the notice
-- went out and, if the person logged in after it, the day until which the account is kept.
ALTER TABLE app_user ADD COLUMN deletion_notice_sent_at TIMESTAMP NULL;
ALTER TABLE app_user ADD COLUMN keep_until DATE NULL;
