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

- Java 25
- Spring Boot 4.1.2 (Web, Validation)
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

Requirements: Java 25.

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### Configuration

| Variable | Purpose |
|---|---|
| `BM_MODE` | `portal` (default, accounts with login) or `household` (profiles without password, home network only) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB connection |
| `BM_INITIAL_ADMIN_EMAIL`, `BM_INITIAL_ADMIN_PASSWORD` | initial admin (portal) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` (587), `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP for registration emails (STARTTLS required) |
| `BM_MAIL_FROM` | sender address (default `noreply@bike-maintainer.de`) |
| `BM_PUBLIC_URL` | address of the app used in email links, e.g. `https://app.bike-maintainer.de` |
| `BM_ALTCHA_HMAC_KEY` | secret for the registration captcha, e.g. `openssl rand -hex 32` |

In portal mode the backend refuses to start without `BM_ALTCHA_HMAC_KEY`, and without `SPRING_MAIL_HOST` unless the
Spring profile `local` is active. At most `bm.mail.daily-limit` (100) emails are sent per day; unverified accounts are
deleted after 7 days.

**Profile `local` (development):** emails are not sent but written to the log, including the verification link, and a
fixed ALTCHA key is used (`application-local.properties`). `./mvnw spring-boot:run` and the development
`compose.yaml` activate it; the published image never does. Never use it in production.

## Static analysis

`./mvnw verify` runs **SpotBugs** with the **FindSecBugs** security rules (effort `Max`, threshold `Medium`) and fails
on any finding; CI uploads the results to Security → Code scanning. Generated code (OpenAPI, MapStruct, Lombok) is
excluded via `spotbugs-exclude.xml` and `lombok.config`.

For a finding: fix the code. If it is a false positive, suppress it at the spot with
`@SuppressFBWarnings(value = "…", justification = "…")` – a broad entry in `spotbugs-exclude.xml` only with a reason.
Report without failing: `./mvnw verify -Dspotbugs.failOnError=false`.

## Docker image

CI publishes a multi-arch image (`linux/amd64`, `linux/arm64`) to the GitHub Container Registry,
`ghcr.io/stargazer74/bike-maintainer-backend` – **only for release tags**. Pull requests and pushes to `main` only
build and test; nothing is published.

| Git tag | Image tags |
|---|---|
| `vX.Y.Z` | `X.Y.Z`, `X.Y`, `X`, `latest` |

Release builds set the Maven version to the tag version, so `info.app.version` and the JAR name match the release.

```bash
docker pull ghcr.io/stargazer74/bike-maintainer-backend:1
```

**Version numbers:** `main` always carries the *upcoming* version as `X.Y.Z-SNAPSHOT` in `pom.xml` (Maven
convention); backend and frontend always share one version number.

**Creating a release:**

1. Tag the version from `main` without `-SNAPSHOT` in this repo and in `bike-maintainer-frontend` (e.g. `v0.3.0` for
   `0.3.0-SNAPSHOT`) and push the tags. CI stops the release build before publishing if the tag does not match the
   pom version.
2. Bump `main` to the next snapshot via PR in both repos – next minor or patch, as needed:
   `./mvnw versions:set -DnewVersion=0.4.0-SNAPSHOT -DgenerateBackupPoms=false` here,
   `npm version --no-git-tag-version 0.4.0-SNAPSHOT` in the frontend.

Compose files for running the app (pulling these
images) live in [bike-maintainer-deploy](https://github.com/stargazer74/bike-maintainer-deploy). The `compose.yaml`
in this repo is the development setup and builds the backend from source.

## Status

Early development stage – the project currently consists of the Spring Boot
scaffolding including architecture/naming convention tests. The actual domain
(vehicles, maintenance plans, history) is not yet implemented.
