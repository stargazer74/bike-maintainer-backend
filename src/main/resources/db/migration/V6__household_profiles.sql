-- Household mode: profiles are users without email and password, identified by a display name.
ALTER TABLE app_user
    MODIFY email VARCHAR(255) NULL;

ALTER TABLE app_user
    ADD COLUMN display_name VARCHAR(50) NULL;

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_display_name UNIQUE (display_name);

-- The initial admin owns the existing vehicles; naming it makes them usable in household mode right away.
-- Derived table, because MariaDB does not allow selecting from the table being updated.
UPDATE app_user
SET display_name = 'Admin'
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM app_user WHERE role = 'ADMIN') AS initial_admin);
