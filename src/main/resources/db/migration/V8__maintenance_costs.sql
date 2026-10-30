-- Materials used for a maintenance log entry, optionally assigned to one of the performed tasks.
CREATE TABLE maintenance_log_material
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id      BIGINT         NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    part_number VARCHAR(100)   NULL,
    quantity    DECIMAL(10, 3) NOT NULL,
    unit        VARCHAR(20)    NOT NULL,
    price       DECIMAL(10, 2) NULL,
    task_id     BIGINT         NULL,
    position    INT            NOT NULL,
    CONSTRAINT fk_maintenance_log_material_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_log_material_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id)
);

CREATE INDEX idx_maintenance_log_material_log_id ON maintenance_log_material (log_id);
CREATE INDEX idx_maintenance_log_material_task_id ON maintenance_log_material (task_id);

-- Labor cost and currency per entry. Existing entries get EUR (the default currency of every existing user).
ALTER TABLE maintenance_log ADD COLUMN labor_cost DECIMAL(10, 2) NULL;
ALTER TABLE maintenance_log ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

-- Preferred currency of the user, applied to new entries.
ALTER TABLE app_user ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';
