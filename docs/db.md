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
  └──1:n──> vehicle
              ├──1:n──> maintenance_task
              └──1:n──> maintenance_log ──m:n──> maintenance_task   (via maintenance_log_task)

SPRING_SESSION ──1:n──> SPRING_SESSION_ATTRIBUTES   (technical, login sessions)
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

**Initial admin (V4).** The migration creates one `ADMIN` account whose email comes from the Flyway placeholder `initialAdminEmail` (`BM_INITIAL_ADMIN_EMAIL`, default `admin@localhost`; stored trimmed and lower-cased). All vehicles that existed before user accounts are assigned to it. The placeholder is only read when V4 runs, so `BM_INITIAL_ADMIN_EMAIL` must be set **before** the first start of that version; changing it later does not rename the account. The password cannot be hashed in SQL: the account starts without one, and on every startup `InitialAdminComponentImpl` sets `BM_INITIAL_ADMIN_PASSWORD` (BCrypt) if the account still has no password. An existing password is never overwritten.

## Tables

### `app_user`
A user account. Login is by email and password; the session is stored in `SPRING_SESSION`.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `email` | VARCHAR(255), UNIQUE | Login name; stored trimmed and lower-cased, compared case-insensitively |
| `password_hash` | VARCHAR(100), nullable | Hash with algorithm prefix, e.g. `{bcrypt}…` (Spring `DelegatingPasswordEncoder`). Null until a password is set — such an account cannot log in |
| `role` | VARCHAR(20) | `USER` or `ADMIN` |
| `active` | BOOLEAN | False = deactivated by an admin; login is rejected with `ACCOUNT_DISABLED` |
| `email_verified` | BOOLEAN | Whether the email address was confirmed; login is rejected with `EMAIL_NOT_VERIFIED` until it is. The initial admin is created verified |
| `failed_login_attempts` | INT | Consecutive failed logins; reset on success |
| `locked_until` | TIMESTAMP, nullable | Set after too many failed logins (`bm.security.lockout-*`, default 10 attempts / 15 minutes); logins are rejected with 429 until then |
| `language` | VARCHAR(5) | Preferred language, `de` or `en` (UI, emails, PDF report) |
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
| `created_at` | TIMESTAMP | Row creation time |

A single visit typically covers several tasks (e.g. an oil change combined with a valve clearance check) with the same date and mileage — which of the `maintenance_task`s were covered is recorded in `maintenance_log_task`, not on this table directly.

### `maintenance_log_task`
Join table between `maintenance_log` and `maintenance_task`: records which tasks were covered by which visit.

| Column | Type | Meaning |
|---|---|---|
| `log_id` | BIGINT, FK → `maintenance_log.id`, part of composite PK | The visit |
| `task_id` | BIGINT, FK → `maintenance_task.id`, part of composite PK | The task performed during that visit |

No extra columns: the visit's date/mileage/notes live on `maintenance_log` and apply identically to every task linked to it.

### `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES`
Login sessions, managed entirely by Spring Session JDBC (schema from `schema-mysql.sql` of spring-session-jdbc, created by Flyway V5 because `spring.session.jdbc.initialize-schema=never`). Storing sessions in the database keeps users logged in across backend restarts and deployments. Not accessed by application code.

- `PRINCIPAL_NAME` holds the id of the logged-in `app_user` (not the email), so that all sessions of a user can be found and ended, e.g. after a password change or deactivation.
- `SPRING_SESSION_ATTRIBUTES` holds the serialized session attributes (among them the security context); rows are deleted together with their session (`ON DELETE CASCADE`).
- Sessions expire after 8 hours of inactivity (`server.servlet.session.timeout`); Spring Session removes expired rows itself.
