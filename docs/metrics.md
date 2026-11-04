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
