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
vehicle
  ├──1:n──> maintenance_task
  └──1:n──> maintenance_log ──m:n──> maintenance_task   (via maintenance_log_task)
```

- A `vehicle` owns its own `maintenance_task` definitions and its own `maintenance_log` entries.
- A `maintenance_log` entry (one workshop/maintenance visit) can cover multiple `maintenance_task`s at once, and a task can appear in multiple log entries over its lifetime.

## Tables

### `vehicle`
A physical vehicle owned by the user.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT, PK | Surrogate key |
| `name` | VARCHAR(255) | User-facing label, e.g. "Honda CBR 1996" |
| `type` | VARCHAR(20) | `MOTORCYCLE` or `CAR` |
| `make` | VARCHAR(100) | Manufacturer, e.g. "Honda" |
| `model` | VARCHAR(100) | Model name, e.g. "CBR 900RR" |
| `model_year` | INT | Model year (named `model_year`, not `year`, because `YEAR` is a reserved keyword in H2/MariaDB) |
| `current_mileage` | INT | Current odometer reading; the baseline against which task due-dates are calculated |
| `created_at` | TIMESTAMP | Row creation time |
| `updated_at` | TIMESTAMP | Last update time |

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
