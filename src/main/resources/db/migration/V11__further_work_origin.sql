-- Further work (backend#127): a one-time task created by the new work of a maintenance log remembers that log, so
-- deleting the log, or removing the task from it, can delete the task too. Planned tasks keep NULL.
ALTER TABLE maintenance_task ADD COLUMN created_by_log_id BIGINT NULL;
ALTER TABLE maintenance_task
    ADD CONSTRAINT fk_maintenance_task_created_by_log FOREIGN KEY (created_by_log_id) REFERENCES maintenance_log (id)
        ON DELETE SET NULL;

-- Existing data has no origin: a one-time task contained in exactly one log counts as further work of that log
-- (decided in backend#127; a planned one-time task done once is treated the same way).
UPDATE maintenance_task
SET created_by_log_id = (SELECT MIN(link.log_id) FROM maintenance_log_task link WHERE link.task_id = maintenance_task.id)
WHERE one_time = TRUE
  AND (SELECT COUNT(*) FROM maintenance_log_task link WHERE link.task_id = maintenance_task.id) = 1;
