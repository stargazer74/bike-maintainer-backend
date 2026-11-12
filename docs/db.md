# Database

## Technology

| Environment | Database | Notes |
|---|---|---|
| Production | MariaDB | `mariadb-java-client` driver, run locally via `compose.yaml` |
| Tests (unit/integration) | H2 (in-memory) | `MODE=MariaDB` compatibility mode, so the same Flyway migrations run unmodified against both databases |

Schema is managed exclusively via **Flyway** migrations (`src/main/resources/db/migration`). `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never generates or changes schema, it only validates entities against what Flyway created.

Note: Flyway's Spring Boot autoconfiguration lives in its own module (`org.springframework.boot:spring-boot-flyway`), separate from `flyway-core`. Both must be on the classpath — without the former, Flyway is silently never invoked (no error, the app just starts without a schema).

## Schema overview

```
app_user
  ├──1:n──> vehicle
  │           ├──1:1──> vehicle_specification   (target values, optional)
  │           ├──1:n──> maintenance_task
  │           └──1:n──> maintenance_log ──m:n──> maintenance_task   (via maintenance_log_task)
  │                       ├──1:n──> maintenance_log_material ──n:1──> maintenance_task (optional)
  │                       ├──1:n──> measurement_valve
  │                       └──1:1──> measurement_brakes / measurement_chain / measurement_tires
  ├──1:n──> user_token      (email links: verification, password reset)
  └──1:n──> device_login    (apps that stay logged in)

SPRING_SESSION ──1:n──> SPRING_SESSION_ATTRIBUTES   (technical, login sessions)
mail_quota                                          (technical, emails per day)
```

The diagram in `db-schema.drawio` shows the same schema.

- Every `vehicle` belongs to exactly one `app_user`. Tasks and logs belong to a user only indirectly, through their vehicle. The API scopes every vehicle lookup to the logged-in user (`VehicleRepository.findByIdAndUser_Id`); vehicles of other users, including their tasks, logs and PDF report, are answered with 404 as if they did not exist.
- A `vehicle` owns its own `maintenance_task` definitions and its own `maintenance_log` entries.
- A `maintenance_log` entry (one workshop/maintenance visit) can cover multiple `maintenance_task`s at once, and a task can appear in multiple log entries over its lifetime.

## Migrations

| Version | Content |
|---|---|
| V1 | `vehicle`, `maintenance_task`, `maintenance_log`, `maintenance_log_task` |
| V2 | `vehicle.model_year` becomes NOT NULL (backfill 1900) with a 1900–2100 check |
| V3 | `vehicle.first_registration_date` (backfill: January 1st of `model_year`) |
| V4 | `app_user`, initial admin, `vehicle.user_id` (existing vehicles are assigned to the initial admin) |
| V5 | Spring Session JDBC tables `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` |
| V6 | Household profiles: `app_user.email` nullable, `app_user.display_name` (unique); the initial admin is named "Admin" |
| V7 | `user_token` (email links), `mail_quota` (daily email limit) |
| V8 | `maintenance_log_material`, `maintenance_log.labor_cost`/`currency`, `app_user.currency` |
| V9 | `device_login` (apps that stay logged in) |
| V10 | `vehicle_specification` (target values), `measurement_valve`, `measurement_brakes`, `measurement_chain`, `measurement_tires` |
| V14 | `app_user.deletion_notice_sent_at`, `app_user.keep_until` (deletion of expired accounts) |

**Initial admin (V4).** The migration creates one `ADMIN` account whose email comes from the Flyway placeholder `initialAdminEmail` (`BM_INITIAL_ADMIN_EMAIL`, default `admin@localhost`; stored trimmed and lower-cased). All vehicles that existed before user accounts are assigned to it. The placeholder is only read when V4 runs, so `BM_INITIAL_ADMIN_EMAIL` must be set **before** the first start of that version; changing it later does not rename the account. The password cannot be hashed in SQL: the account starts without one, and on every startup `InitialAdminComponentImpl` sets `BM_INITIAL_ADMIN_PASSWORD` (BCrypt) if the account still has no password. An existing password is never overwritten.

## Tables

### `app_user`
A user account. In portal mode login is by email and password; in household mode a profile without email and password
is chosen by its display name. The session is stored in `SPRING_SESSION` in both modes.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `email` | VARCHAR(255), UNIQUE, nullable | Login name (portal); stored trimmed and lower-cased, compared case-insensitively. Null for household profiles |
| `display_name` | VARCHAR(50), UNIQUE, nullable | Name of a household profile; null for portal accounts (the UI shows the email instead) |
| `password_hash` | VARCHAR(100), nullable | Hash with algorithm prefix, e.g. `{bcrypt}…` (Spring `DelegatingPasswordEncoder`). Null until a password is set — such an account cannot log in |
| `role` | VARCHAR(20) | `USER` or `ADMIN` |
| `active` | BOOLEAN | False = deactivated by an admin; login is rejected with `ACCOUNT_DISABLED` |
| `email_verified` | BOOLEAN | Whether the email address was confirmed; login is rejected with `EMAIL_NOT_VERIFIED` until it is. The initial admin is created verified |
| `failed_login_attempts` | INT | Consecutive failed logins; reset on success |
| `locked_until` | TIMESTAMP, nullable | Set after too many failed logins (`bm.security.lockout-*`, default 10 attempts / 15 minutes); logins are rejected with 429 until then |
| `language` | VARCHAR(5) | Preferred language, `de` or `en` (UI, emails, PDF report) |
| `currency` | VARCHAR(3) | Currency for new maintenance entries (ISO 4217, e.g. `EUR`) |
| `deletion_notice_sent_at` | TIMESTAMP, nullable | Portal: when the notice about the coming deletion of the expired account was sent (V14); null without a pending notice. The account is deleted at the earliest `bm.retention.notice-period` after it |
| `keep_until` | DATE, nullable | Portal: the account is not deleted before this day; set when the person logs in after a deletion notice (V14). Login times are not recorded otherwise |
| `created_at` | TIMESTAMP | Row creation time |
| `updated_at` | TIMESTAMP | Last update time |

### `vehicle`
A physical vehicle owned by one user.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `name` | VARCHAR(255) | User-facing label, e.g. "Honda CBR 1996" |
| `type` | VARCHAR(20) | `MOTORCYCLE` or `CAR` |
| `make` | VARCHAR(100) | Manufacturer, e.g. "Honda" |
| `model` | VARCHAR(100) | Model name, e.g. "CBR 900RR" |
| `model_year` | INT | Model year, 1900–2100 (named `model_year`, not `year`, because `YEAR` is a reserved keyword in H2/MariaDB) |
| `first_registration_date` | DATE | Date of first registration (Erstzulassung); independent of `model_year` |
| `current_mileage` | INT | Current odometer reading; the baseline against which task due-dates are calculated |
| `created_at` | TIMESTAMP | Row creation time |
| `updated_at` | TIMESTAMP | Last update time |
| `user_id` | BIGINT, FK → `app_user.id` | Owner of the vehicle; only this user can see or change the vehicle and everything below it |

### `maintenance_task`
A maintenance plan item: a recurring (or one-time) job defined for one specific vehicle, e.g. "change engine oil, every 6 months or 6000 km". Exists independently of whether it has ever been performed — this is what makes it possible to show a task as due/overdue even before its first log entry.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `vehicle_id` | BIGINT, FK → `vehicle.id` | The vehicle this task applies to. Tasks are not shared between vehicles, even identical models — each vehicle has its own independent set |
| `name` | VARCHAR(255) | e.g. "Check valve clearance" |
| `description` | VARCHAR(1000) | Optional free-text detail |
| `interval_km` | INT, nullable | Recurring interval in kilometers. Null if the task is purely time-based |
| `interval_months` | INT, nullable | Recurring interval in months. Null if the task is purely mileage-based |
| `first_due_km` | INT, nullable | First due mileage, if it differs from `interval_km` (e.g. a break-in service at 1000 km before the regular 6000 km cadence starts) |
| `first_due_months` | INT, nullable | First due time, analogous to `first_due_km` |
| `one_time` | BOOLEAN | If true, the task is due exactly once (at `first_due_km`/`first_due_months`) and never recurs |
| `active` | BOOLEAN | Soft on/off switch, so a task can be retired without deleting its history |
| `created_by_log_id` | BIGINT, nullable, FK → `maintenance_log.id` (ON DELETE SET NULL) | Further work: the log whose new work created this one-time task. Deleting that log, or leaving the task out when the log is edited, deletes the task too unless another log contains it. Null for planned tasks |

A task is due as soon as **either** its km or time threshold is reached — `current_mileage >= next_due_km` **or** today `>= next_due_date` — mirroring "whichever comes first" from a paper service schedule. `next_due_km`/`next_due_date` are not stored; they are derived from the task's most recent linked `maintenance_log` (via `maintenance_log_task`), or from `first_due_km`/`first_due_months` if no log exists yet.

### `maintenance_log`
One row per maintenance/workshop visit on a vehicle — what was checked/replaced on a given day at a given mileage.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `vehicle_id` | BIGINT, FK → `vehicle.id` | The vehicle this visit was performed on |
| `performed_at` | DATE | Date the visit took place |
| `mileage_at_performed` | INT | Odometer reading at the time of the visit |
| `notes` | VARCHAR(1000) | Optional free-text notes about the visit |
| `labor_cost` | DECIMAL(10,2), nullable | Labor cost of the visit |
| `currency` | VARCHAR(3) | Currency of `labor_cost` and of the material prices; taken from the user's currency when the entry is created |
| `created_at` | TIMESTAMP | Row creation time |

A single visit typically covers several tasks (e.g. an oil change combined with a valve clearance check) with the same date and mileage — which of the `maintenance_task`s were covered is recorded in `maintenance_log_task`, not on this table directly.

### `maintenance_log_task`
Join table between `maintenance_log` and `maintenance_task`: records which tasks were covered by which visit.

| Column | Type | Meaning |
|---|---|---|
| `log_id` | BIGINT, FK → `maintenance_log.id`, part of composite PK | The visit |
| `task_id` | BIGINT, FK → `maintenance_task.id`, part of composite PK | The task performed during that visit |

No extra columns: the visit's date/mileage/notes live on `maintenance_log` and apply identically to every task linked to it.

### `maintenance_log_material`
Parts and consumables used during a visit, in the order entered.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `log_id` | BIGINT, FK → `maintenance_log.id` (`ON DELETE CASCADE`) | The visit |
| `name` | VARCHAR(200) | e.g. "Engine oil 10W-40" |
| `part_number` | VARCHAR(100), nullable | Manufacturer part number |
| `quantity` | DECIMAL(10,3) | Amount in `unit` |
| `unit` | VARCHAR(20) | `PIECE`, `LITER`, `MILLILITER`, `KILOGRAM`, `GRAM`, `METER` |
| `price` | DECIMAL(10,2), nullable | Total price of this line in the visit's currency |
| `task_id` | BIGINT, FK → `maintenance_task.id`, nullable | The performed task the material belongs to (must be one of the visit's tasks) |
| `position` | INT | Order within the visit |

### `vehicle_specification`
Target values from the workshop manual, at most one row per vehicle; all columns except the key are optional. Used to
prefill worksheets, to check measured values (`OK`/`OUT_OF_RANGE`/`UNKNOWN`) and by the shim calculator. Lengths in mm
(3 decimal places), pressures in bar (2 decimal places). Deleted together with the vehicle.

| Column | Type | Meaning |
|---|---|---|
| `vehicle_id` | BIGINT, PK, FK → `vehicle.id` (`ON DELETE CASCADE`) | The vehicle |
| `cylinders` / `valves_per_cylinder` | INT, nullable | 1–8 / 2–4 |
| `valve_adjustment` | VARCHAR(10), nullable | `SHIM` or `SCREW` (no shim calculation for `SCREW`) |
| `intake_clearance_min/max`, `exhaust_clearance_min/max` | DECIMAL(6,3), nullable | Valve clearance range |
| `shim_step`, `shim_min`, `shim_max` | DECIMAL(6,3), nullable | Grid and available range of shim sizes |
| `brake_pad_min_front/rear`, `brake_disc_min_front/rear` | DECIMAL(6,3), nullable | Minimum pad and disc thickness |
| `chain_slack_min/max` | DECIMAL(6,3), nullable | Chain slack range |
| `chain_wear_links`, `chain_wear_max_length` | INT / DECIMAL(6,3), nullable | Wear limit: maximum length over this number of links |
| `tire_pressure_front/rear` | DECIMAL(4,2), nullable | Target pressure (a measurement within ±0.1 bar is `OK`) |
| `tread_depth_min` | DECIMAL(6,3), nullable | Minimum tread depth |

### `measurement_valve`, `measurement_brakes`, `measurement_chain`, `measurement_tires`
Measured values of a maintenance log, at most one set per template (one row per valve for `measurement_valve`, one row
per log for the others: unique `log_id`). Saving a log with `measurements` replaces all sets of that log; templates not
sent are deleted. Only vehicle data, nothing about people (the paper worksheet's "done by" is not stored). Deleted
together with the log (`log_id` FK → `maintenance_log.id`, `ON DELETE CASCADE`). The tables with free texts are created
with `utf8mb4`.

| Table | Columns |
|---|---|
| `measurement_valve` | `cylinder`, `valve` (`INTAKE`/`EXHAUST`), `valve_index` (1–2), `measured`, `shim_old`, `shim_new` (DECIMAL(6,3)); unique per log/cylinder/valve/index |
| `measurement_brakes` | `pad_front/rear`, `disc_front/rear` (DECIMAL(6,3)), `disc_condition_front/rear`, `fluid_condition` (VARCHAR(100)) |
| `measurement_chain` | `slack`, `wear_length` (DECIMAL(6,3)), `wear_links` (INT), `sprocket_condition`, `rear_sprocket_condition` (VARCHAR(100)), `cleaned`, `lubricated` (BOOLEAN) |
| `measurement_tires` | `pressure_front/rear` (DECIMAL(4,2)), `tread_front/rear` (DECIMAL(6,3)), `dot_front/rear` (VARCHAR(4), week+year), `condition_front/rear` (VARCHAR(100)) |

### `user_token`
One-time links sent by email (`EMAIL_VERIFICATION`, `PASSWORD_RESET`). Only the SHA-256 hash of the token is stored, so
a leaked database contains no usable links.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `user_id` | BIGINT, FK → `app_user.id` (`ON DELETE CASCADE`) | Account the link belongs to |
| `type` | VARCHAR(30) | `EMAIL_VERIFICATION` or `PASSWORD_RESET` |
| `token_hash` | CHAR(64), UNIQUE | SHA-256 of the token (hex) |
| `expires_at` | TIMESTAMP | 24 hours (verification) or 1 hour (reset) after issue |
| `used_at` | TIMESTAMP, nullable | Set when used or replaced by a newer link; such a token is invalid |
| `created_at` | TIMESTAMP | Issue time |

Used and expired tokens are deleted by the nightly cleanup.

### `mail_quota`
Emails sent per day (all kinds together) for the global daily limit (`bm.mail.daily-limit`). Stored so that a restart
does not reset the count.

| Column | Type | Meaning |
|---|---|---|
| `quota_date` | DATE, PK | Day |
| `sent_count` | INT | Emails sent on that day |

### `device_login`
Apps that stay logged in (portal mode): a device token is exchanged for a normal session whenever the session expired;
every exchange rotates the token. Only SHA-256 hashes are stored; no IP addresses or locations.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key; stored in the session attribute `bm.deviceId` of sessions started by the device |
| `user_id` | BIGINT, FK → `app_user.id` (`ON DELETE CASCADE`) | Account |
| `name` | VARCHAR(100) | Name chosen on the device, e.g. the phone model |
| `token_hash` | CHAR(64), UNIQUE | SHA-256 of the current token |
| `previous_token_hash` | CHAR(64), UNIQUE, nullable | SHA-256 of the token before the last exchange; presenting it again (later than 60 s after the exchange) removes the device |
| `created_at` | TIMESTAMP | Registration |
| `last_used_at` | TIMESTAMP | Last login or exchange; after 90 days without use (`bm.security.device-login-idle-timeout`) the device is removed |

Devices are also removed on logout from their session, by the user in the web app, after a password change or reset,
and when an admin deactivates the account. The table is created with `utf8mb4` explicitly (names may contain emoji).

### `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES`
Login sessions, managed entirely by Spring Session JDBC (schema from `schema-mysql.sql` of spring-session-jdbc, created by Flyway V5 because `spring.session.jdbc.initialize-schema=never`). Storing sessions in the database keeps users logged in across backend restarts and deployments. Not accessed by application code.

- `PRINCIPAL_NAME` holds the id of the logged-in `app_user` (not the email), so that all sessions of a user can be found and ended, e.g. after a password change or deactivation.
- `SPRING_SESSION_ATTRIBUTES` holds the serialized session attributes (among them the security context); rows are deleted together with their session (`ON DELETE CASCADE`).
- Sessions expire after 8 hours of inactivity (`server.servlet.session.timeout`); Spring Session removes expired rows itself.
