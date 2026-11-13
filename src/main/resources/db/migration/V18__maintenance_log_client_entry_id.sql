-- Random id (UUID) a client may send with a new maintenance log, so that resending it from an offline queue after a
-- lost answer returns the existing log instead of creating a duplicate (Epic #159, #162). Unique per vehicle; no
-- personal or device data, deleted with the log.
ALTER TABLE maintenance_log ADD COLUMN client_entry_id CHAR(36) NULL;

ALTER TABLE maintenance_log ADD CONSTRAINT uk_maintenance_log_client_entry UNIQUE (vehicle_id, client_entry_id);
