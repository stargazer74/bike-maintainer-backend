# Device Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apps stay logged in (portal mode) via a rotating, revocable device token that is exchanged for a normal
session; the web app lists and removes devices.

**Architecture:** Contract-first: spec 2.10.0 adds four endpoints. The backend stores only SHA-256 hashes of the
current and previous token in `device_login`, creates devices through the existing `LoginAuthenticationProviderImpl`
and starts sessions through `ISessionComponent`, marking them with the session attribute `bm.deviceId`. Revocation
hooks sit in logout, password change/reset and the nightly cleanup. The Angular account page gets a device list.

**Tech Stack:** OpenAPI 3.0.3; Java 25, Spring Boot 4, Spring Session JDBC, JPA + Flyway, JUnit 5/AssertJ/Mockito,
Cucumber; Angular 22, Vitest, Transloco.

**Spec:** `bike-maintainer-backend/docs/specs/2026-11-06-device-login-design.md`

## Global Constraints

- Repos `bike-maintainer-api`, `-backend`, `-frontend`, all on branch `feature/device-login`; commits reference
  api#25, backend#103, frontend#79; every commit ends with `Co-Authored-By: opencode <noreply@opencode.ai>`.
- Spec version `2.10.0`; all changes additive; new schemas `DeviceLoginRequest`, `DeviceSessionRequest`,
  `DeviceLoginResponse`, `DeviceResponse`; path parameter `deviceId` (`int64`).
- `deviceName` 1–100 chars; `deviceToken` 256 random bits, Base64url without padding (43 chars), request field
  `deviceToken` with `minLength: 43`, `maxLength: 43`.
- Portal mode only; household mode answers 404 for all four endpoints.
- Session attribute name `bm.deviceId` (Long). Setting `bm.security.device-login-idle-timeout=90d`.
- Metrics `bm.auth.device_login{result=success|bad_credentials|locked|disabled|unverified}`,
  `bm.auth.device_session{result=success|invalid|reused}`, registered with 0 at startup; rate-limit groups
  `device_login`, `device_session`.
- Never log or return token or hash except `deviceToken` in the two login responses; logs carry user and device ids
  only.
- Backend rules: no `var`; components only behind interfaces (`I…Component`); no extra `@SpringBootTest`;
  `./mvnw verify` green with 90 % line/branch coverage.
- Frontend rules: OnPush, signals, `rxResource`, hand-styled `.btn` classes, texts in `de.json` and `en.json`.

## Review Focus

- **Two exchanges with the same token at once** (background job and foreground) → one succeeds, the other gets
  401, the device is **not** revoked. Test in Task 3 (conditional update) and Task 6 (Cucumber).
- **Session created by a device, then that device removed in the web app** → the app's session ends immediately
  (next request 401). Test in Task 4 and Task 6.
- **`deviceName` with only spaces or emoji** → trimmed; blank after trim → 400; emoji stored and shown intact
  (`utf8mb4`). Test in Task 5.
- **Device of a deactivated account** → `device-session` 401, device row kept; works again after reactivation.
  Test in Task 3.
- **Password reset while the app holds a valid session** → all devices gone and other sessions ended; the app's next
  exchange is 401. Test in Task 6.

---

### Task 1: API spec 2.10.0 (api repo)

**Files:**
- Modify: `bike-maintainer-api/src/main/resources/maintenance-api.yaml`

**Interfaces:**
- Produces: operations `deviceLogin`, `createDeviceSession`, `listDevices`, `deleteDevice` (tag `Auth`), generating
  `AuthApi.deviceLogin(DeviceLoginRequest)`, `createDeviceSession(DeviceSessionRequest)`, `listDevices()`,
  `deleteDevice(Long deviceId)` in the backend and the same names in the Angular `AuthService`.
  `DeviceLoginResponse { user: UserResponse (required), deviceToken: string (required) }`;
  `DeviceResponse { id int64, name string, createdAt date-time, lastUsedAt date-time, current boolean }` (all required).

- [ ] **Step 1:** `deviceName`: `type: string, minLength: 1, maxLength: 100, pattern: '\S'` (at least one non-space
  character). Add the four operations with the responses from the spec table (reuse `BadRequest`,
  `Unauthorized`, `NotFound`, `TooManyRequests`; `403` of `device-login` documented like `login`), `security: [ ]` on
  `device-login` and `device-session`, `version: 2.10.0`, tag description: household mode answers 404 for the device
  endpoints; extend the `logout` description („from a device session it also removes that device“).
- [ ] **Step 2:** `mvn clean install` → `BUILD SUCCESS`.
- [ ] **Step 3: Commit** `Auth: device login endpoints for apps (issue #25, spec 2.10.0)`.

### Task 2: Table, entity, repository

**Files:**
- Create: `src/main/resources/db/migration/V9__device_login.sql`,
  `persistence/model/DeviceLogin.java`, `persistence/repository/DeviceLoginRepository.java`,
  test `persistence/repository/DeviceLoginRepositoryTest.java` (`@DataJpaTest`, profile `test`)

**Interfaces:**
- Produces `DeviceLogin` (Lombok builder; fields `id`, `user` (`@ManyToOne(fetch = LAZY)` `AppUser`), `name`,
  `tokenHash`, `previousTokenHash`, `createdAt`, `lastUsedAt`) and `DeviceLoginRepository extends
  JpaRepository<DeviceLogin, Long>` with:
  - `Optional<DeviceLogin> findByTokenHash(String tokenHash)`
  - `Optional<DeviceLogin> findByPreviousTokenHash(String tokenHash)`
  - `List<DeviceLogin> findAllByUser_IdOrderByLastUsedAtDesc(Long userId)`
  - `Optional<DeviceLogin> findByIdAndUser_Id(Long id, Long userId)`
  - `@Modifying int rotate(Long id, String oldHash, String newHash, LocalDateTime now)` –
    `UPDATE … SET previousTokenHash = :oldHash, tokenHash = :newHash, lastUsedAt = :now WHERE id = :id AND tokenHash = :oldHash`
  - `@Modifying int deleteAllByUser_Id(Long userId)` (JPQL delete)
  - `@Modifying int deleteUnusedSince(LocalDateTime cutoff)` (`lastUsedAt < :cutoff`)

- [ ] **Step 1: Write `DeviceLoginRepositoryTest`**: `rotate` with the current hash returns 1 and swaps hashes;
  a second `rotate` with the same old hash returns 0; deleting the user removes its devices (cascade);
  `deleteUnusedSince` removes only older rows; a name with emoji (`"Pixel 8 🏍"`) round-trips.
- [ ] **Step 2: Run** `./mvnw test -Dtest=DeviceLoginRepositoryTest` → fails.
- [ ] **Step 3: Implement** migration (columns, unique `token_hash`, unique `previous_token_hash`, index `user_id`,
  FK `ON DELETE CASCADE`, `utf8mb4` like the other tables), entity and repository.
- [ ] **Step 4: Run** the test and `FlywayMigrationTest` → pass.
- [ ] **Step 5: Commit** `Device login: table, entity and repository (#103)`.

### Task 3: `DeviceLoginComponentImpl` – login and exchange

**Files:**
- Create: `component/IDeviceLoginComponent.java`, `component/DeviceLoginComponentImpl.java`,
  `component/helper/DeviceTokens.java`, tests `component/DeviceLoginComponentImplTest.java`,
  `component/helper/DeviceTokensTest.java`
- Modify: `configuration/SecurityProperties.java` (+ `@NotNull Duration deviceLoginIdleTimeout`),
  `application.properties` (`bm.security.device-login-idle-timeout=90d`)

**Interfaces:**
- Consumes: `DeviceLoginRepository` (Task 2), `AuthenticationManager`, `ISessionComponent.startSession`,
  `ICurrentUserComponent`, `UserMapper`, `Clock`, `MeterRegistry`, `SecurityProperties`.
- Produces:
  - `DeviceTokens.generate(): String` (43-char Base64url), `DeviceTokens.hash(String token): String` (64-char lower hex
    SHA-256).
  - `IDeviceLoginComponent` (portal only):
    - `DeviceLoginResponse login(DeviceLoginRequest request, HttpServletRequest req, HttpServletResponse res)` –
      throws `AuthenticationFailedException`, `AccountUnusableException`, `TooManyRequestsException` like
      `IAuthComponent.login`.
    - `DeviceLoginResponse exchange(DeviceSessionRequest request, HttpServletRequest req, HttpServletResponse res)` –
      throws `AuthenticationFailedException("Invalid device token")` for every rejection.
  - Session attribute `bm.deviceId` set after `startSession` (constant `DeviceLoginComponentImpl.SESSION_ATTRIBUTE`).

- [ ] **Step 1: Write tests** (`DeviceTokensTest`: length 43, URL-safe alphabet, two calls differ, hash is 64 hex and
  stable). `DeviceLoginComponentImplTest` with mocks and a fixed clock:
  - `login` success → device saved with trimmed name, `lastUsedAt = now`, stored hash = `hash(returned token)`,
    session started, attribute set, `bm.auth.device_login{result=success}` = 1.
  - `login` with `BadCredentialsException` / `TooManyRequestsException` / `AccountUnusableException(ACCOUNT_DISABLED)` /
    `(EMAIL_NOT_VERIFIED)` → nothing saved, matching `result` counted, exception mapped like `AuthComponentImpl`.
  - `exchange` valid → `rotate` called with old/new hash, new token returned, session started,
    `device_session{result=success}`.
  - `rotate` returns 0 (lost race) → `AuthenticationFailedException`, device **not** deleted, `result=invalid`.
  - token matches only `previousTokenHash` → device deleted, `result=reused`, exception.
  - unknown token → exception, `result=invalid`; `lastUsedAt` older than 90 days → device deleted, exception.
  - account inactive or unverified → exception, device kept.
  - all counters exist with 0 right after construction.
- [ ] **Step 2: Run** → fails.
- [ ] **Step 3: Implement** (explicit constructor to register counters, like `AccountDeletionComponentImpl`;
  `@Transactional` on `exchange` with `noRollbackFor = AuthenticationFailedException.class` so a reuse deletion
  sticks).
- [ ] **Step 4: Run** `./mvnw test -Dtest='DeviceLoginComponentImplTest,DeviceTokensTest'` → pass.
- [ ] **Step 5: Commit** `Device login: create devices and exchange tokens for sessions (#103)`.

### Task 4: Listing, removal and revocation hooks

**Files:**
- Modify: `IDeviceLoginComponent`/`DeviceLoginComponentImpl` (list, delete, revokeCurrent, revokeAll, cleanup),
  `ISessionComponent`/`SessionComponentImpl` (+ `invalidateSessionsOfDevice`), `AuthComponentImpl.logout`,
  `PasswordComponentImpl` (`changePassword`, `confirmReset`), `AccountCleanupComponentImpl`, and their tests.

**Interfaces:**
- Produces on `IDeviceLoginComponent`:
  - `List<DeviceResponse> listDevices(Long currentDeviceId)` (current user, `current` = id equals argument)
  - `boolean deleteDevice(Long deviceId)` (false if unknown or foreign; also ends the device's sessions)
  - `void revokeDevice(Long deviceId)` (used by logout; ignores unknown ids)
  - `void revokeAllOf(Long userId)`
  - `int deleteUnused()` (cutoff = now − idle timeout)
- Produces on `ISessionComponent`: `void invalidateSessionsOfDevice(Long userId, Long deviceId)` – deletes sessions of
  the user whose attribute `bm.deviceId` equals `deviceId`.
- `AuthComponentImpl.logout` reads `bm.deviceId` from the session **before** invalidating and calls
  `revokeDevice` through `ObjectProvider<IDeviceLoginComponent>` (absent in household mode).

- [ ] **Step 1: Write tests**: list marks `current`; delete own → true and sessions of that device invalidated;
  foreign → false, nothing deleted; logout with attribute revokes, without attribute does not touch devices;
  `changePassword` and `confirmReset` call `revokeAllOf(userId)`; cleanup calls `deleteUnused` and logs the count;
  `invalidateSessionsOfDevice` keeps sessions with another or no `bm.deviceId`.
- [ ] **Step 2: Run** → fails. **Step 3: Implement.** **Step 4: Run** the touched test classes → pass.
- [ ] **Step 5: Commit** `Device login: list, remove and revoke devices (#103)`.

### Task 5: Controller, security, rate limit, docs

**Files:**
- Modify: `controller/AuthController.java`, `configuration/SecurityConfiguration.java` (permit `device-login`,
  `device-session`), `configuration/HouseholdSecurityConfiguration.java` (`/api/v1/auth/device-login`,
  `/api/v1/auth/device-session`, `/api/v1/auth/devices/**` in `PORTAL_ONLY_ENDPOINTS`),
  `configuration/RateLimitFilter.java` (two groups), `docs/metrics.md`, `README.md` (setting row),
  tests `AuthControllerTest`, `RateLimitFilterTest`

**Interfaces:**
- Consumes: `IDeviceLoginComponent` via `ObjectProvider` (as `IPasswordComponent`).

- [ ] **Step 1: Write controller tests**: `device-login` 200 with body `{user, deviceToken}`; `deviceName` `"   "` → 400
  (spec pattern from Task 1); `device-session` 401 maps `AuthenticationFailedException`; `GET /devices` 200; `DELETE` 204 / 404.
  `RateLimitFilterTest` rows `POST, /api/v1/auth/device-login, device_login` and `POST,
  /api/v1/auth/device-session, device_session`.
- [ ] **Step 2: Run** → fails. **Step 3: Implement** (controller passes `servletRequestAttributes()` like `login`;
  `listDevices` reads `bm.deviceId` from the current session). **Step 4: Run** → pass.
- [ ] **Step 5: Commit** `Device login: endpoints, security and rate limits (#103)`.

### Task 6: Cucumber end to end

**Files:**
- Create: `src/test/resources/features/device-login.feature`, `cucumber/portal/DeviceLoginSteps.java`
- Modify: `features/household.feature` (one scenario: the three device paths → 404),
  `cucumber/steps/DatabaseCleanupHooks.java` (`DELETE FROM device_login` before users)

**Interfaces:**
- Steps (new): `I log in on a device {string} as {string} with password {string}` (stores token in the scenario),
  `my session expires` (deletes all rows of `SPRING_SESSION*`, keeps cookies), `the app exchanges its device token`,
  `the app exchanges its previous device token`, `the app exchanges its device token twice at the same time`,
  `{int} days pass` (uses the existing `MutableClock`), `I remove the device {string} in the web app`.

- [ ] **Step 1: Write scenarios** per spec „Tests“ (login → expire → exchange → `GET /api/v1/vehicles` 200; previous
  token → 401 and `GET /api/v1/auth/devices` empty; parallel exchange → exactly one 200, device still listed;
  logout revokes; password change revokes all; password reset revokes all; account deletion; 91 days → 401;
  foreign device delete → 404; device removed in web → app's next request 401; rate limit after 10 bad
  `device-login`; `current` flag) and the household scenario.
- [ ] **Step 2: Run** `./mvnw verify` → green, coverage ≥ 90 %.
- [ ] **Step 3: Commit** `Device login: Cucumber scenarios (#103)`.

### Task 7: Web app device list (frontend repo)

**Files:**
- Create: `src/app/pages/account-settings/device-list/device-list.{ts,html,scss,spec.ts}`
- Modify: `account-settings.html` (section between password and delete), `public/i18n/de.json`, `en.json`
  (`accountSettings.devices.*`, extended `accountSettings.changed`)

**Interfaces:**
- Consumes: generated `AuthService.listDevices()`, `deleteDevice(id)`; `confirmAction` from
  `shared/confirm-dialog`.
- Produces: standalone `DeviceList` component (`app-device-list`), no inputs.

- [ ] **Step 1: `npm run generate:api`**, then write `device-list.spec.ts`: renders name, „angemeldet seit“ and
  „zuletzt genutzt“ in `de`; empty list shows the hint text from the spec; „Abmelden“ → confirm dialog with the
  device name → `DELETE /api/v1/auth/devices/7` → list reloads; cancel → no request; delete answering 404 → list
  reloads, no error; load error → `account.errors.unknown` text.
- [ ] **Step 2: Run** `npx ng test --watch=false --include 'src/app/pages/account-settings/**/*.spec.ts'` → fails.
- [ ] **Step 3: Implement** (`rxResource` with `reload()` after delete).
- [ ] **Step 4: Run** `npm run format:check && npm run lint && npm run test:coverage && npx ng build` → green.
- [ ] **Step 5: Commit** `Account page: list and remove logged-in devices (issue #79)`.

### Task 8: Wrap-up

- [ ] **Step 1:** Ask the user for the OK to push the three branches and open the PRs (`Closes #25`, `#103`, `#79`,
  labels api/backend/frontend, cross-linked); then comment the PRs in the tickets and in epic backend#13 (standing
  rule).
- [ ] **Step 2:** Remind the user of the privacy policy sentence from the spec (after the 0.11.0 release).
