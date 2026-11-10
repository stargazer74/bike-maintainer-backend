# Metriken für die Überwachung

Das Backend stellt seine Metriken im Prometheus-Format unter `/actuator/prometheus` bereit – **nur auf dem
Management-Port 8081**, der im Betrieb nicht über den Proxy erreichbar ist. Eingesammelt werden sie vom
Überwachungs-Stack in `bike-maintainer-deploy/monitoring/` (Grafana Alloy → Prometheus).

## Gemeinsame Tags

Jede Metrik trägt:

| Tag | Werte |
|---|---|
| `application` | `bike-maintainer` |
| `mode` | `portal` oder `household` (aus `bm.mode`) |

## Standardmetriken (Spring Boot / Micrometer)

- `http_server_requests_seconds_*` – Anfragen nach Methode, Pfad-Muster (`uri`), Status; mit Histogramm-Buckets für
  Perzentile (p50/p95)
- `jvm_*` – Speicher, Garbage Collection, Threads
- `hikaricp_*` – Datenbank-Verbindungspool
- `tomcat_*`, `process_*`, `system_*`, `application_ready_time_seconds`

## Fachliche Metriken

Präfix `bm.`; Zähler mit festen Tag-Werten (Anmeldung, Geräte-Anmeldung, Mail-Zustellung) und `bm.accounts.deleted` sind ab dem Start mit 0 registriert. Im Prometheus-Format werden Punkte zu Unterstrichen, Zähler bekommen `_total`
(`bm.auth.login` → `bm_auth_login_total`). Tags haben nur wenige feste Werte – nie Nutzer-IDs, E-Mail-Adressen,
Namen oder freie Pfade.

| Name | Art | Tags | Bedeutung |
|---|---|---|---|
| `bm.auth.login` | Zähler | `result`: `success`, `bad_credentials` (unbekannte E-Mail oder falsches Passwort), `locked` (Konto gesperrt), `disabled` (Konto deaktiviert), `unverified` (E-Mail nicht bestätigt) | Anmeldeversuche nach Ergebnis |
| `bm.auth.lockout` | Zähler | – | Konto wegen zu vieler Fehlversuche gesperrt |
| `bm.auth.registration` | Zähler | `step`: `started` (neues Konto angelegt, Bestätigungsmail unterwegs), `verified` (E-Mail-Adresse bestätigt) | Registrierungen; Versuche mit bereits registrierter Adresse zählen nicht |
| `bm.auth.password_reset` | Zähler | `step`: `requested` (Mail an ein aktives Konto unterwegs), `completed` (neues Passwort gesetzt) | Passwort-Zurücksetzen; Anfragen für unbekannte Adressen zählen nicht |
| `bm.auth.device_login` | Zähler | `result`: wie `bm.auth.login` | Anmeldungen aus Apps mit „angemeldet bleiben“ |
| `bm.auth.device_session` | Zähler | `result`: `success`, `invalid` (unbekanntes, abgelaufenes oder entferntes Geräte-Token, Konto unbenutzbar), `reused` (schon getauschtes Token erneut benutzt – Gerät entfernt) | Tausch Geräte-Token gegen neue Sitzung |
| `bm.accounts.deleted` | Zähler | – | Von ihren Nutzern selbst gelöschte Konten (Portal) |
| `bm.protection.altcha_failed` | Zähler | – | Abgelehnte ALTCHA-Lösungen (fehlend, ungültig, abgelaufen, schon benutzt) |
| `bm.protection.rate_limited` | Zähler | `group`: `login`, `registration`, `verification_resend`, `password_reset`, `password_change`, `account_deletion`, `device_login`, `device_session` | Wegen des Rate-Limits pro IP abgewiesene Anfragen (429) |
| `bm.mail.sent` | Zähler | `type`: `verification`, `password_reset`, `registration_exists`; `result`: `queued` (an den Versand übergeben), `quota_exceeded` (wegen Tageslimit verworfen) | Mails, die die App verschicken wollte |
| `bm.mail.delivery` | Zähler | `result`: `success`, `failure` | Zustellversuche beim SMTP-Server (asynchron, daher getrennt von `bm.mail.sent`) |
| `bm.mail.quota.used` | Gauge | – | Heute gezählte Mails; im Speicher gehalten und jede Minute aus der Datenbank nachgelesen, damit das Abholen der Metriken nie auf die Datenbank wartet |
| `bm.mail.quota.limit` | Gauge | – | Tageslimit (`bm.mail.daily-limit`) |
| `bm.report.generated` | Timer | `type`: `report` (Wartungsbericht), `checklist` (Wartungs-Checkliste), `worksheet` (Arbeitsblatt); `result`: `success`, `failure` | Anzahl und Dauer der PDF-Erzeugung (nur das Rendern, ohne Datenbankzugriffe) |

## Logs

Im Portal setzt `portal/compose.yaml` `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`: jede Logzeile ist ECS-JSON
(`log.level`, `log.logger`, `message`, …). Während einer Anfrage enthält sie zusätzlich `http.method` und
`http.route` – das Pfad-Muster wie `/api/v1/vehicles/{vehicleId}`, nie die konkrete URL oder Query-Parameter.
Ohne die Variable (Haushalt, lokale Entwicklung) bleibt es bei lesbaren Textzeilen. Logs enthalten keine
E-Mail-Adressen, Tokens oder Passwörter.
