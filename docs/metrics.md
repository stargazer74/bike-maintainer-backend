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

Präfix `bm.`; im Prometheus-Format werden Punkte zu Unterstrichen, Zähler bekommen `_total`
(`bm.auth.login` → `bm_auth_login_total`). Tags haben nur wenige feste Werte – nie Nutzer-IDs, E-Mail-Adressen,
Namen oder freie Pfade.

| Name | Art | Tags | Bedeutung |
|---|---|---|---|
| `bm.auth.login` | Zähler | `result`: `success`, `bad_credentials` (unbekannte E-Mail oder falsches Passwort), `locked` (Konto gesperrt), `disabled` (Konto deaktiviert), `unverified` (E-Mail nicht bestätigt) | Anmeldeversuche nach Ergebnis |
| `bm.auth.lockout` | Zähler | – | Konto wegen zu vieler Fehlversuche gesperrt |
| `bm.auth.registration` | Zähler | `step`: `started` (neues Konto angelegt, Bestätigungsmail unterwegs), `verified` (E-Mail-Adresse bestätigt) | Registrierungen; Versuche mit bereits registrierter Adresse zählen nicht |
| `bm.auth.password_reset` | Zähler | `step`: `requested` (Mail an ein aktives Konto unterwegs), `completed` (neues Passwort gesetzt) | Passwort-Zurücksetzen; Anfragen für unbekannte Adressen zählen nicht |
| `bm.protection.altcha_failed` | Zähler | – | Abgelehnte ALTCHA-Lösungen (fehlend, ungültig, abgelaufen, schon benutzt) |
| `bm.protection.rate_limited` | Zähler | `group`: `login`, `registration`, `verification_resend`, `password_reset`, `password_change` | Wegen des Rate-Limits pro IP abgewiesene Anfragen (429) |
| `bm.mail.sent` | Zähler | `type`: `verification`, `password_reset`, `registration_exists`; `result`: `queued` (an den Versand übergeben), `quota_exceeded` (wegen Tageslimit verworfen) | Mails, die die App verschicken wollte |
| `bm.mail.delivery` | Zähler | `result`: `success`, `failure` | Zustellversuche beim SMTP-Server (asynchron, daher getrennt von `bm.mail.sent`) |
| `bm.mail.quota.used` | Gauge | – | Heute gezählte Mails; `NaN`, wenn die Datenbank nicht lesbar ist |
| `bm.mail.quota.limit` | Gauge | – | Tageslimit (`bm.mail.daily-limit`) |
