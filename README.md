# Bike Maintainer

Backend service for digitizing maintenance schedules for motorcycles and cars.

## Motivation

Vehicle service manuals typically contain a maintenance table that specifies, for each
task (e.g. "check valve clearance", "change engine oil"), the time and/or mileage
interval at which it must be repeated — whichever comes first:

| Task | Frequency | 1000 km | 6000 km | 12000 km | 18000 km | ... |
|---|---|---|---|---|---|---|
| Change engine oil | 6 months | ● | ● | ● | ● | ... |
| Clean/adjust spark plug | – | | ● | ● | ● | ... |
| Change brake fluid | 2 years | | | | ● | ... |

In practice this table is usually tracked on paper or not at all, so it's easy to lose
track of when a task was last done and when it's due again.

**Bike Maintainer** digitizes exactly that: for each vehicle, a maintenance plan with
its tasks and intervals is stored. Based on the current mileage and date, the app shows
which tasks are currently due or overdue, and logs when a task was actually performed.

## Core features (planned)

- **Vehicle management** – create and maintain multiple vehicles (motorcycle, car,
  ...) with their current mileage.
- **Maintenance plans** – define tasks per vehicle/model with an interval, either
  time-based (e.g. "every 6 months"), mileage-based (e.g. "every 6000 km"), or both
  combined ("whichever comes first").
- **Due overview** – based on current mileage and date, calculate which tasks are due,
  upcoming, or overdue.
- **Maintenance history** – log when (date + mileage) a task was last performed, as the
  basis for the next due-date calculation.

## Tech stack

- Java 21
- Spring Boot 4.1.1 (Web, Validation)
- Lombok, MapStruct
- Maven (wrapper: `./mvnw`)
- ArchUnit for architecture tests

## Architecture

The project follows a layered architecture:

```
Controller -> Component -> Service
```

- **Controller** – REST endpoints, must not be accessed by any other layer.
- **Component** – business logic, may be accessed by `Service` or `Controller`.
- **Service** – domain logic/data access, may only be accessed by `Component`.

These rules are enforced via ArchUnit tests (`src/test/java/.../archunit`).

## Setup & running

Requirements: Java 21.

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test
```

## Status

Early development stage – the project currently consists of the Spring Boot
scaffolding including architecture/naming convention tests. The actual domain
(vehicles, maintenance plans, history) is not yet implemented.
