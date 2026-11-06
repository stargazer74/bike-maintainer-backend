# Geräte-Anmeldung („angemeldet bleiben“) – Design

Stand: 2026-11-06 · Epic stargazer74/bike-maintainer-backend#104, Teilprojekt A der Android-App (Teilprojekt B: Android-App Version 1, eigene Spec) · Tickets:
stargazer74/bike-maintainer-api#25, stargazer74/bike-maintainer-backend#103, stargazer74/bike-maintainer-frontend#79

## Ziel

Apps auf dem eigenen Gerät bleiben im Portalmodus angemeldet, ohne dass die kurze Sitzung der Web-App (8 Stunden
ohne Nutzung) verlängert wird. Die Android-App kann damit auch im Hintergrund (Benachrichtigungen über fällige
Wartungen) aktuelle Daten vom Server holen.

**Erfolg:**

- Nach einer Anmeldung mit „angemeldet bleiben“ muss sich die App erst nach 90 Tagen ohne Nutzung, nach einer
  Passwortänderung, nach dem Abmelden oder nach dem Entfernen des Geräts in der Web-App neu anmelden.
- Ein kopiertes und wiederverwendetes Token wird erkannt; das Gerät wird sofort abgemeldet.
- Auf der Kontoseite der Web-App sind alle angemeldeten Geräte sichtbar und einzeln abmeldbar.
- Der normale Login, die Sitzungen und die Sicherheitskette der Web-App bleiben unverändert.

## Abgrenzung

- **Nur Portalmodus.** Im Haushaltsmodus antworten alle neuen Endpunkte 404; Apps wählen dort das gespeicherte
  Profil neu (kein Passwort).
- **Kein Bearer-Token bei jeder Anfrage.** Das Geräte-Token wird nur gegen eine normale Sitzung getauscht (Cookie +
  CSRF wie in der Web-App); es gibt keine zweite Sicherheitskette.
- **Die Web-App benutzt keine Geräte-Tokens** und erscheint nicht in der Geräteliste.
- Nicht enthalten: Geräte-Widerruf bei Admin-Deaktivierung (kommt mit stargazer74/bike-maintainer-backend#9; bis dahin
  lehnt `device-session` deaktivierte Konten ab), Push-Benachrichtigungen, Anmeldung per Biometrie.

## Schnittstelle (API, Spec 2.10.0, additiv)

| Endpunkt | Zweck | Antworten |
|---|---|---|
| `POST /api/v1/auth/device-login` `DeviceLoginRequest { email, password, deviceName }` | Anmeldung mit „angemeldet bleiben“ | `200 DeviceLoginResponse { user, deviceToken }` + Sitzungs-Cookie; `400`, `401`, `403` (`EMAIL_NOT_VERIFIED`, `ACCOUNT_DISABLED`), `429` wie beim Login; `404` im Haushaltsmodus |
| `POST /api/v1/auth/device-session` `DeviceSessionRequest { deviceToken }` | neue Sitzung mit dem Geräte-Token; liefert ein **neues** Token | `200 DeviceLoginResponse` + Sitzungs-Cookie; `400`; `401` (ungültig, abgelaufen, widerrufen, wiederverwendet, Konto deaktiviert oder unbestätigt – immer dieselbe Antwort); `429`; `404` im Haushaltsmodus |
| `GET /api/v1/auth/devices` | eigene angemeldete Geräte | `200 [ DeviceResponse { id, name, createdAt, lastUsedAt, current } ]`; `401`; `404` im Haushaltsmodus |
| `DELETE /api/v1/auth/devices/{deviceId}` | eigenes Gerät abmelden | `204`; `401`; `404` (unbekannt, fremd oder Haushaltsmodus) |

- `deviceName`: 1–100 Zeichen, vom Gerät gewählt (z. B. Modellname); wird nur angezeigt.
- `deviceToken`: 256 Bit Zufall, Base64url ohne Padding (43 Zeichen). Erscheint nur in den beiden Antworten oben,
  nie in Listen oder Logs.
- `current`: `true` für das Gerät, mit dem die anfragende Sitzung gestartet wurde.
- `device-login` und `device-session` brauchen keine Anmeldung, aber wie der Login ein CSRF-Token.
- `POST /api/v1/auth/logout` aus einer Gerätesitzung widerruft zusätzlich dieses Gerät (Beschreibung ergänzen,
  Verhalten für die Web-App unverändert).
- Ein Gerät lässt sich nur mit E-Mail und Passwort anlegen, nie aus einer bestehenden Sitzung.

## Backend

### Datenbank (`V9__device_login.sql`)

Tabelle `device_login`:

| Spalte | Typ | Inhalt |
|---|---|---|
| `id` | `BIGINT` PK, auto | |
| `user_id` | `BIGINT NOT NULL`, FK `app_user(id)` `ON DELETE CASCADE` | Konto |
| `name` | `VARCHAR(100) NOT NULL` | Gerätename |
| `token_hash` | `CHAR(64) NOT NULL`, eindeutig | SHA-256 (hex) des aktuellen Tokens |
| `previous_token_hash` | `CHAR(64)`, eindeutig, nullable | SHA-256 des vorherigen Tokens (Wiederverwendung erkennen) |
| `created_at` | `DATETIME NOT NULL` | Anlage |
| `last_used_at` | `DATETIME NOT NULL` | letzte Anmeldung oder letzter Tausch |

Index auf `user_id`. Keine IP-Adressen, kein Ort, keine Gerätekennungen außer dem Namen.

### Ablauf

`DeviceLoginComponentImpl` (`@Conditional(OperatingModeCondition.Portal.class)`):

- **device-login:** Prüfung über den bestehenden `LoginAuthenticationProviderImpl` (dieselben Fehlversuche,
  Kontosperre, Fehlercodes und Metrik `bm.auth.login` wie beim Login). Danach Gerät anlegen (`last_used_at` = jetzt),
  Token erzeugen (`SecureRandom`), Sitzung über `ISessionComponent.startSession` starten, Geräte-ID als
  Sitzungsattribut `bm.deviceId` speichern.
- **device-session:**
  1. Token hashen; Treffer in `token_hash` suchen.
  2. Kein Treffer, aber Treffer in `previous_token_hash` → Gerät löschen, `401` (Wiederverwendung).
  3. Kein Treffer → `401`.
  4. `last_used_at` älter als `bm.security.device-login-idle-timeout` → Gerät löschen, `401`.
  5. Konto nicht aktiv oder E-Mail nicht bestätigt → `401` (Gerät bleibt; ein reaktiviertes Konto kann es wieder
     nutzen).
  6. Sonst: neues Token, `previous_token_hash` = alter Hash, `token_hash` = neuer Hash, `last_used_at` = jetzt; in
     derselben Transaktion; Sitzung starten, `bm.deviceId` setzen. Eine Kontosperre wegen falscher Passwörter
     blockiert hier nicht (das Token ist kein Rateversuch).
- **Gleichzeitige Tausch-Anfragen mit demselben Token:** Die Aktualisierung läuft per bedingtem `UPDATE … WHERE
  token_hash = :alt`; nur eine Anfrage gewinnt, die andere bekommt `401` und löst **keinen** Widerruf aus (sie hat
  den aktuellen, nicht den vorherigen Hash geschickt).
- **Bekannte Kehrseite:** Bricht die Verbindung nach dem Tausch ab, bevor die App das neue Token gespeichert hat,
  ist das Gerät beim nächsten Versuch abgemeldet (Wiederverwendung). Bewusst in Kauf genommen.

### Widerruf

| Ereignis | Wirkung |
|---|---|
| Logout aus einer Gerätesitzung (`bm.deviceId` gesetzt) | dieses Gerät löschen |
| `DELETE /auth/devices/{id}` | dieses Gerät löschen und die Sitzungen des Nutzers beenden, deren Attribut `bm.deviceId` diese ID trägt (Suche über `findByPrincipalName`, wie beim Passwortwechsel) |
| Passwort ändern, Passwort zurücksetzen | **alle** Geräte des Kontos löschen |
| Konto löschen | Geräte per `ON DELETE CASCADE` |
| 90 Tage ohne Nutzung | `device-session` lehnt ab; der nächtliche `AccountCleanupComponent` löscht abgelaufene Geräte |

### Schutz

- Rate-Limit pro IP (`RateLimitFilter`): Gruppen `device_login` (`POST /api/v1/auth/device-login`) und
  `device_session` (`POST /api/v1/auth/device-session`).
- `SecurityConfiguration`: beide Endpunkte ohne Anmeldung erlaubt; `HouseholdSecurityConfiguration`: alle neuen
  Pfade in `PORTAL_ONLY_ENDPOINTS`.
- Fehlerantworten von `device-session` verraten nicht, warum ein Token nicht gilt.

### Einstellungen

`bm.security.device-login-idle-timeout=90d` (in `SecurityProperties`, `@NotNull Duration`).

### Metriken und Logs

| Metrik | Tags | Bedeutung |
|---|---|---|
| `bm.auth.device_login` | `result`: `success`, `bad_credentials`, `locked`, `disabled`, `unverified` | Anmeldungen mit „angemeldet bleiben“ |
| `bm.auth.device_session` | `result`: `success`, `invalid`, `reused` | Tausch Token gegen Sitzung; `reused` = Wiederverwendung erkannt |
| `bm.protection.rate_limited` | neue `group`-Werte `device_login`, `device_session` | |

Alle Zähler ab dem Start mit 0 registriert. Logs nur mit Nutzer- und Geräte-ID; nie Token, Hash oder E-Mail.
`docs/metrics.md` ergänzen.

### Tests

- Component-Tests für `DeviceLoginComponentImpl` (Anlegen, Tausch, Wiederverwendung, Ablauf, inaktives Konto,
  gleichzeitiger Tausch über den bedingten Update), Widerruf in `PasswordComponentImpl`, Cleanup.
- Controller-Tests für die vier Endpunkte (Statuscodes, Validierung `deviceName`).
- Cucumber `device-login.feature`: anmelden → Sitzung ablaufen lassen (Sitzungen löschen) → Token tauschen → Daten
  abrufen; altes Token erneut → `401` und Gerät weg; Logout widerruft; Passwortänderung widerruft alle; Konto löschen;
  90 Tage (Uhr vorstellen); fremdes Gerät löschen → `404`; Rate-Limit; Haushaltsmodus → `404`;
  `GET /auth/devices` markiert `current`.
- `./mvnw verify` grün (inkl. 90 % Abdeckung).

## Web-App

- Kontoseite `/konto`: neuer Abschnitt **„Angemeldete Geräte“** zwischen „Passwort ändern“ und „Konto löschen“.
  Je Gerät Name, „angemeldet seit“, „zuletzt genutzt“ (`localDate`-Pipes) und Knopf „Abmelden“ mit der vorhandenen
  Bestätigungsabfrage („‚{{name}}‘ abmelden? Die App muss sich danach neu anmelden.“); danach Liste neu laden.
- Leere Liste: „Keine Geräte angemeldet. Geräte erscheinen hier, wenn du dich in der Android-App mit ‚angemeldet
  bleiben‘ anmeldest.“
- Fehler beim Laden: allgemeine Fehlermeldung; `404` beim Abmelden gilt als erledigt.
- Hinweis nach Passwortänderung ergänzt: „Auch die Apps müssen sich neu anmelden.“
- Daten über `rxResource`, Texte de/en (Transloco). Tests: Liste, leere Liste, Abmelden mit Bestätigung,
  Abbrechen, Fehler beim Laden; `npm test`, `npm run lint`, `npx ng build` grün.

## Folgen außerhalb des Codes

- **Datenschutzerklärung** (de/en), Abschnitt „Konto in der App“: „Wenn du dich in der Android-App mit ‚angemeldet
  bleiben‘ anmeldest, speichern wir den Gerätenamen, den Zeitpunkt der Anmeldung und der letzten Nutzung sowie einen
  Hash des Anmelde-Schlüssels; nach 90 Tagen ohne Nutzung wird der Eintrag gelöscht. Unter ‚Konto‘ kannst du Geräte
  jederzeit abmelden.“ – plus Cookie-Tabelle unverändert (die App nutzt dieselben Cookies).
- **Monitoring:** Kein neuer Alarm nötig; `bm.auth.device_session{result="reused"}` erscheint im Backend-Dashboard,
  falls gewünscht (eigene Entscheidung bei der Umsetzung im deploy-Repo).

## Umsetzung

Drei PRs mit gleichem Branch-Namen `feature/device-login` (api, backend, frontend), Reihenfolge api → backend →
frontend; Release als Minor-Version (Backend und Frontend 0.11.0).
