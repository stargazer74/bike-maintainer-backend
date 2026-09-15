CREATE TABLE vehicle
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    make             VARCHAR(100),
    model            VARCHAR(100),
    model_year       INT,
    current_mileage  INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE maintenance_task
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id        BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    interval_km       INT,
    interval_months   INT,
    first_due_km      INT,
    first_due_months  INT,
    one_time          BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_maintenance_task_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id)
);

CREATE TABLE maintenance_log
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id            BIGINT    NOT NULL,
    performed_at          DATE      NOT NULL,
    mileage_at_performed  INT       NOT NULL,
    notes                 VARCHAR(1000),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_log_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id)
);

CREATE TABLE maintenance_log_task
(
    log_id   BIGINT NOT NULL,
    task_id  BIGINT NOT NULL,
    PRIMARY KEY (log_id, task_id),
    CONSTRAINT fk_maintenance_log_task_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id),
    CONSTRAINT fk_maintenance_log_task_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id)
);

CREATE INDEX idx_maintenance_task_vehicle_id ON maintenance_task (vehicle_id);
CREATE INDEX idx_maintenance_log_vehicle_id ON maintenance_log (vehicle_id);
CREATE INDEX idx_maintenance_log_task_task_id ON maintenance_log_task (task_id);
