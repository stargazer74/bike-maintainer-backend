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
| `BM_MAIL_FROM` | sender address (default: `SPRING_MAIL_USERNAME`) |
| `BM_PUBLIC_URL` | address of the app used in email links, e.g. `https://app.example.com` |
| `BM_WEBSITE_URL` | public website (portal only), e.g. `https://www.example.com`; the app links to it and to its legal notice and privacy policy. Unset = no links; anything but an http(s) address stops the startup |
| `BM_ALTCHA_HMAC_KEY` | secret for the registration captcha, e.g. `openssl rand -hex 32` |
| `BM_SUBSCRIPTION_TRIAL_DAYS` | length of the free trial in days (portal); no default, the value is a decision of the deployment |
| `BM_POLAR_ACCESS_TOKEN`, `BM_POLAR_WEBHOOK_SECRET`, `BM_POLAR_PRODUCT_ID` | subscription via Polar (portal): organization access token, `whsec_…` secret of the webhook endpoint `https://<app>/api/v1/webhooks/polar`, id of the yearly product |
| `BM_POLAR_API_URL` | Polar API, default `https://api.polar.sh`; `https://sandbox-api.polar.sh` for the sandbox |
| `BM_POLAR_EARLY_END_ENABLED` | `true` = a subscription cancelled after its first year is ended automatically one month after the cancellation and the rest of its last payment refunded pro rata (§ 309 no. 9 BGB); needs the token scopes `orders:read` and `refunds:write`. Default `false`: the admins only get an email and do it by hand in Polar |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` for JSON log lines (portal operation, collected by the monitoring); unset = readable text |

In portal mode the backend refuses to start without `BM_ALTCHA_HMAC_KEY` and `BM_SUBSCRIPTION_TRIAL_DAYS`, and
without `SPRING_MAIL_HOST` and the three `BM_POLAR_*` settings unless the Spring profile `local` is active (there the
checkout answers 503 without Polar). At most `bm.mail.daily-limit` (100) emails are sent per day; unverified accounts are
deleted after 7 days. Expired accounts (trial or paid period over) are deleted 12 months after their access ended
(`bm.retention.period`), never without a notice by email 30 days before (`bm.retention.notice-period`, at most
`bm.retention.notice-daily-limit` (50) notices per day, not counted towards the mail limit); logging in after the
notice keeps the account for another 12 months. Login times are not recorded.

### Login & first start (portal mode)

```
Internet ──443──▶ TLS proxy ──▶ frontend (nginx) ──/api/──▶ backend ──▶ MariaDB
                                (only upstream)              (internal)  (internal)
```

Only the frontend container is reachable from outside; it forwards `/api/` to the backend in the internal Docker
network. Backend and database publish no ports. The compose files, `.env.example` and the proxy setup live in
`bike-maintainer-deploy/portal/` (see its README).

1. **Initial admin:** set `BM_INITIAL_ADMIN_EMAIL` **before the first start** – migration V4 creates the admin account
   with this address and reads it only once. `BM_INITIAL_ADMIN_PASSWORD` (at least 10 characters) is set as the
   password on startup while the account has none; it is never overwritten afterwards. Log in and change it right
   away.
2. **SMTP:** `SPRING_MAIL_*` for registration, verification and password-reset emails (STARTTLS); the sender is the
   SMTP login unless `BM_MAIL_FROM` is set.
3. **`BM_PUBLIC_URL`:** the address users open (e.g. `https://app.example.com`); email links point there.
4. **Captcha:** `BM_ALTCHA_HMAC_KEY` (`openssl rand -hex 32`).
5. **Subscription:** `BM_SUBSCRIPTION_TRIAL_DAYS` and the `BM_POLAR_*` settings. In Polar, create the yearly product
   and a webhook endpoint for `https://<app>/api/v1/webhooks/polar` with the `subscription.*` events (format raw); the
   backend only uses an event as a trigger and fetches the current state of the subscription from Polar. Polar only
   learns the email address (for the invoice) and a random customer reference, never the internal account id.
   The access token needs `checkouts:write`, `customer_sessions:write`, `subscriptions:read`/`subscriptions:write`
   and, with
   `BM_POLAR_EARLY_END_ENABLED=true`, also `orders:read` and `refunds:write`. The early end runs nightly
   (`bm.cleanup.cron`): revoke the subscription, refund the days after the end pro rata of the last payment's net
   amount (Polar adds the tax), email the admins; after `bm.polar.early-end.max-attempts` (3) failed nights, or when
   Polar reports something unexpected, the admins are asked by email to finish it by hand.

Further accounts register themselves (email verification required); admins manage roles and lock accounts under
`/admin/nutzer` in the web app.

**Profile `local` (development):** emails are not sent but written to the log, including the verification link, and a
fixed ALTCHA key is used (`application-local.properties`). Swagger UI (`/swagger-ui.html`) and `/v3/api-docs` are
only available in this profile. `./mvnw spring-boot:run` and the development
`compose.yaml` activate it; the published image never does. Never use it in production.

### Monitoring

Metrics in Prometheus format at `/actuator/prometheus` on the **management port 8081** only (not reachable through
the proxy); list and meaning in [`docs/metrics.md`](docs/metrics.md). With `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`
every log line is ECS JSON; during a request it carries `http.method` and the route pattern `http.route`
(e.g. `/api/v1/vehicles/{vehicleId}`), never the concrete URL or query string. Logs never contain email addresses,
tokens or passwords.

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
