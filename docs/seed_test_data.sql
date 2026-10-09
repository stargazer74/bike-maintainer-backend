-- Testdaten: 2 Motorräder inkl. Wartungsaufgaben und Wartungshistorie.
-- Nicht Teil der Flyway-Migrationen (bewusst getrennt von db/migration),
-- damit Tests und Produktionsschema unberührt bleiben.
-- Ausführen z. B. mit:
--   docker compose exec -T mariadb mysql -u maintenance -pmaintenance maintenance < docs/seed_test_data.sql
--
-- Setzt Migration V4 voraus: Die Fahrzeuge gehören dem Initial-Admin (BM_INITIAL_ADMIN_EMAIL),
-- sichtbar also nach dem Login mit dessen Zugangsdaten.

SET @owner_id = (SELECT id FROM app_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1);

--
-- Daten für Tabelle vehicle
--

INSERT INTO vehicle (id, name, type, make, model, model_year, first_registration_date, current_mileage, created_at, updated_at, user_id) VALUES
(1, 'Kawasaki VN800 Classic', 'MOTORCYCLE', 'Kawasaki', 'VN800 Classic', 2005, '2005-01-01', 41664, '2026-10-01 11:04:53', '2026-10-01 16:06:55', @owner_id),
(2, 'Online Bestia 125', 'MOTORCYCLE', 'Online', 'Bestia 125', 2021, '2021-01-01', 11604, '2026-10-01 11:10:20', '2026-10-01 16:07:34', @owner_id);

--
-- Daten für Tabelle maintenance_task
--

INSERT INTO maintenance_task (id, vehicle_id, name, description, interval_km, interval_months, first_due_km, first_due_months, one_time, active) VALUES
(1, 2, 'Batterie - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(2, 2, 'Bowdenzüge - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(3, 2, 'Kupplungsspiel - überprüfen, reinigen, ersetzen wenn erforderlich, einstellen', NULL, 4000, 12, 4000, 12, 0, 1),
(4, 2, 'Ventilspiel - überprüfen, reinigen, ersetzen wenn erforderlich, einstellen', NULL, 4000, 12, 4000, 12, 0, 1),
(5, 2, 'Zündkerze - ersetzen', NULL, 4000, 12, 4000, 12, 0, 1),
(6, 2, 'Luftfilter - überprüfen', NULL, 4000, 12, 4000, 12, 0, 1),
(7, 2, 'Luftfilter - ersetzen', NULL, 8000, 24, 8000, 24, 0, 1),
(8, 2, 'Kraftstofffilter - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(9, 2, 'Kraftstoffleitungen - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(10, 2, 'Motoröl mit Filter - ersetzen', NULL, 4000, 12, 4000, 12, 0, 1),
(11, 2, 'Kühlsystem mit Schläuchen/Schellen - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(12, 2, 'Kühlflüssigkeit - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(13, 2, 'Radlager; Radlagerspiel - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(14, 2, 'Räder & Reifen; Profiltiefe; Unwucht, Beschädigung - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(15, 2, 'Schwingenlager; Schwingenlagerspiel - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(16, 2, 'Bremsbeläge - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(17, 2, 'Bremssystem inkl. Seile & Gestänge - überprüfen, reinigen, ersetzen wenn erforderlich, füllen', NULL, 4000, 12, 4000, 12, 0, 1),
(18, 2, 'Bremsschläuche & Bremsleitungen - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(19, 2, 'Bremsflüssigkeit - ersetzen', NULL, 4000, 12, 4000, 12, 0, 1),
(20, 2, 'Antriebskette; Kettenspanner, Kettenräder - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(21, 2, 'Federung hinten & vorne inkl. Stoßdämpfer - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(22, 2, 'Teleskopgabel Dichtheit & Funktion -überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(23, 2, 'Teleskopgabelöl - ersetzen', NULL, 8000, 24, 8000, 24, 0, 1),
(24, 2, 'Muttern; Schrauben; Befestigungsteile; Motoraufhängung; Anbauteile; bewegliche Teile -überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(25, 2, 'Elektrische Anlage; Licht; Hupe - überprüfen, reinigen, ersetzen wenn erforderlich', NULL, 4000, 12, 4000, 12, 0, 1),
(26, 2, 'Fehlerspeicher auslesen - überprüfen, reinigen, ersetzen wenn erforderlich, schmieren', NULL, 4000, 12, 4000, 12, 0, 1),
(27, 2, 'Probefahrt & Endkontrolle Verkehrssicherheit - überprüfen', NULL, 4000, 12, 4000, 12, 0, 1),
(28, 1, 'Zündkerzen - reingen & Kontaktabstand einstellen, ersetzen falls notwendig', NULL, 6000, NULL, 6000, NULL, 0, 1),
(29, 1, 'Ventilspiel prüfen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(30, 1, 'Luftansaugventil prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(31, 1, 'Luftfilter-Einsatz reinigen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(32, 1, 'Gasdrehgriff-Spiel prüfen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(33, 1, 'Leerlaufdrehzahl einstellen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(34, 1, 'Motoröl wechseln', NULL, 6000, 6, 6000, 6, 0, 1),
(35, 1, 'Ölfilter ersetzen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(36, 1, 'Kühlschläuche & Verbindeungen prüfen', NULL, 1000, NULL, NULL, NULL, 1, 1),
(37, 1, 'Kühlmittel wechseln', NULL, NULL, 24, NULL, 24, 0, 1),
(38, 1, 'Kupplung einstelle', NULL, 6000, NULL, 6000, NULL, 0, 1),
(39, 1, 'Antriebskette Verschleiß prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(40, 1, 'Antriebskette schmieren', NULL, 600, NULL, 600, NULL, 0, 1),
(41, 1, 'Antriebskettenspannung prüfen', NULL, 1000, NULL, 1000, NULL, 0, 1),
(42, 1, 'Bremsbelag & Bremsklotz Abnutzung prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(43, 1, 'Bremsflüssigkeitsstand prüfen', NULL, 6000, 1, 6000, 1, 0, 1),
(44, 1, 'Bremsflüssigkeit wechseln', NULL, 24000, 24, 24000, 24, 0, 1),
(45, 1, 'Hauptbremszylinder-Manschette & Staubschutz ersetzen', NULL, NULL, 48, NULL, 48, 0, 1),
(46, 1, 'Bremssatteldichtung & Staubschutz ersetzen', NULL, NULL, 48, NULL, 48, 0, 1),
(47, 1, 'Bremsspiel prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(48, 1, 'Bremslichtschalter prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(49, 1, 'Bremszug ersetzen', NULL, 24000, 24, 24000, 24, 0, 1),
(50, 1, 'Lenkung prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(51, 1, 'Steuerkopflager schmieren', NULL, 24000, 24, 24000, 24, 0, 1),
(52, 1, 'Vorderradgabelöl wechseln', NULL, 24000, 24, 24000, 24, 0, 1),
(53, 1, 'Stoßdämperundichtigkeit prüfen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(54, 1, 'Vordergabel Undichtigkeit prüfen', NULL, 12000, NULL, 12000, NULL, 0, 1),
(55, 1, 'Reifenverschleiß prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(56, 1, 'Speichen & Felgen prüfen', NULL, 6000, NULL, 6000, NULL, 0, 1),
(57, 1, 'Schwingenarmbolzen, Uni-Track schmieren', NULL, 12000, NULL, 12000, NULL, 0, 1),
(58, 1, 'Allgemeines Abschmieren', NULL, 12000, NULL, 12000, NULL, 0, 1),
(59, 1, 'Allgemeine Befestigung bewegliche Teile prüfen', NULL, 12000, NULL, 12000, NULL, 0, 1);

--
-- Daten für Tabelle maintenance_log
--

INSERT INTO maintenance_log (id, vehicle_id, performed_at, mileage_at_performed, notes, created_at) VALUES
(1, 2, '2026-03-01', 11100, NULL, '2026-10-01 16:42:14');

--
-- Daten für Tabelle maintenance_log_task
--

INSERT INTO maintenance_log_task (log_id, task_id) VALUES
(1, 1),
(1, 2),
(1, 3),
(1, 4),
(1, 5),
(1, 6),
(1, 10),
(1, 12),
(1, 14),
(1, 16),
(1, 17),
(1, 18),
(1, 19),
(1, 20),
(1, 22),
(1, 25),
(1, 27);
