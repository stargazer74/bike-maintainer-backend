UPDATE vehicle
SET model_year = 1900
WHERE model_year IS NULL;

ALTER TABLE vehicle
    MODIFY model_year INT NOT NULL;

ALTER TABLE vehicle
    ADD CONSTRAINT chk_vehicle_model_year CHECK (model_year BETWEEN 1900 AND 2100);
