-- Target values from the workshop manual, one row per vehicle (Epic #113). All optional. Lengths in mm (3 decimal
-- places), pressures in bar (2 decimal places).
CREATE TABLE vehicle_specification
(
    vehicle_id            BIGINT       NOT NULL PRIMARY KEY,
    cylinders             INT          NULL,
    valves_per_cylinder   INT          NULL,
    valve_adjustment      VARCHAR(10)  NULL,
    intake_clearance_min  DECIMAL(6, 3) NULL,
    intake_clearance_max  DECIMAL(6, 3) NULL,
    exhaust_clearance_min DECIMAL(6, 3) NULL,
    exhaust_clearance_max DECIMAL(6, 3) NULL,
    shim_step             DECIMAL(6, 3) NULL,
    shim_min              DECIMAL(6, 3) NULL,
    shim_max              DECIMAL(6, 3) NULL,
    brake_pad_min_front   DECIMAL(6, 3) NULL,
    brake_pad_min_rear    DECIMAL(6, 3) NULL,
    brake_disc_min_front  DECIMAL(6, 3) NULL,
    brake_disc_min_rear   DECIMAL(6, 3) NULL,
    chain_slack_min       DECIMAL(6, 3) NULL,
    chain_slack_max       DECIMAL(6, 3) NULL,
    chain_wear_links      INT          NULL,
    chain_wear_max_length DECIMAL(6, 3) NULL,
    tire_pressure_front   DECIMAL(4, 2) NULL,
    tire_pressure_rear    DECIMAL(4, 2) NULL,
    tread_depth_min       DECIMAL(6, 3) NULL,
    CONSTRAINT fk_vehicle_specification_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE CASCADE
);

-- Measured values of a maintenance log, at most one set per template; deleted together with the log. Only vehicle
-- data, nothing about people. Free texts in utf8mb4 (may contain any character).
CREATE TABLE measurement_valve
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id      BIGINT        NOT NULL,
    cylinder    INT           NOT NULL,
    valve       VARCHAR(10)   NOT NULL,
    valve_index INT           NOT NULL,
    measured    DECIMAL(6, 3) NULL,
    shim_old    DECIMAL(6, 3) NULL,
    shim_new    DECIMAL(6, 3) NULL,
    CONSTRAINT uk_measurement_valve UNIQUE (log_id, cylinder, valve, valve_index),
    CONSTRAINT fk_measurement_valve_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id) ON DELETE CASCADE
);

CREATE TABLE measurement_brakes
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id               BIGINT        NOT NULL,
    pad_front            DECIMAL(6, 3) NULL,
    pad_rear             DECIMAL(6, 3) NULL,
    disc_front           DECIMAL(6, 3) NULL,
    disc_rear            DECIMAL(6, 3) NULL,
    disc_condition_front VARCHAR(100)  NULL,
    disc_condition_rear  VARCHAR(100)  NULL,
    fluid_condition      VARCHAR(100)  NULL,
    CONSTRAINT uk_measurement_brakes_log UNIQUE (log_id),
    CONSTRAINT fk_measurement_brakes_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id) ON DELETE CASCADE
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE measurement_chain
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id                  BIGINT        NOT NULL,
    slack                   DECIMAL(6, 3) NULL,
    wear_length             DECIMAL(6, 3) NULL,
    wear_links              INT           NULL,
    sprocket_condition      VARCHAR(100)  NULL,
    rear_sprocket_condition VARCHAR(100)  NULL,
    cleaned                 BOOLEAN       NULL,
    lubricated              BOOLEAN       NULL,
    CONSTRAINT uk_measurement_chain_log UNIQUE (log_id),
    CONSTRAINT fk_measurement_chain_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id) ON DELETE CASCADE
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE measurement_tires
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id          BIGINT        NOT NULL,
    pressure_front  DECIMAL(4, 2) NULL,
    pressure_rear   DECIMAL(4, 2) NULL,
    tread_front     DECIMAL(6, 3) NULL,
    tread_rear      DECIMAL(6, 3) NULL,
    dot_front       VARCHAR(4)    NULL,
    dot_rear        VARCHAR(4)    NULL,
    condition_front VARCHAR(100)  NULL,
    condition_rear  VARCHAR(100)  NULL,
    CONSTRAINT uk_measurement_tires_log UNIQUE (log_id),
    CONSTRAINT fk_measurement_tires_log FOREIGN KEY (log_id) REFERENCES maintenance_log (id) ON DELETE CASCADE
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
