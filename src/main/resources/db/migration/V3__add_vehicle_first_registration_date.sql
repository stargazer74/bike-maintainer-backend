ALTER TABLE vehicle
    ADD COLUMN first_registration_date DATE NULL;

-- Backfill with January 1st of model_year, which is a required column present on every existing
-- row — not exact, but the closest available approximation for live data that predates this field.
UPDATE vehicle
SET first_registration_date = CAST(CONCAT(model_year, '-01-01') AS DATE)
WHERE first_registration_date IS NULL;

ALTER TABLE vehicle
    MODIFY first_registration_date DATE NOT NULL;
