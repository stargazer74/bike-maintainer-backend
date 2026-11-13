-- Payment provider Paddle instead of Polar (backend#155). Polar only ever ran against its sandbox, so its
-- subscriptions are test data and are removed; the random customer references of the accounts stay and are reused.
DELETE FROM subscription WHERE provider = 'POLAR';
