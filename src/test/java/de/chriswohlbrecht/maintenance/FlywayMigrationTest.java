package de.chriswohlbrecht.maintenance;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** All migrations on a fresh H2 database in MariaDB mode, without starting Spring. */
class FlywayMigrationTest {

    private Flyway flyway;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .load();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void appliesAllMigrationsSuccessfully() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        assertThat(appliedMigrations).hasSize(12);
        assertThat(appliedMigrations[0].getDescription()).isEqualTo("init schema");
        assertThat(appliedMigrations[0].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[1].getDescription()).isEqualTo("vehicle model year required");
        assertThat(appliedMigrations[1].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[2].getDescription()).isEqualTo("add vehicle first registration date");
        assertThat(appliedMigrations[2].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[3].getDescription()).isEqualTo("add app user");
        assertThat(appliedMigrations[3].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[4].getDescription()).isEqualTo("add spring session");
        assertThat(appliedMigrations[4].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[5].getDescription()).isEqualTo("household profiles");
        assertThat(appliedMigrations[5].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[6].getDescription()).isEqualTo("user token and mail quota");
        assertThat(appliedMigrations[6].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[7].getDescription()).isEqualTo("maintenance costs");
        assertThat(appliedMigrations[7].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[8].getDescription()).isEqualTo("device login");
        assertThat(appliedMigrations[8].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[9].getDescription()).isEqualTo("measurements");
        assertThat(appliedMigrations[9].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[10].getDescription()).isEqualTo("further work origin");
        assertThat(appliedMigrations[10].getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(appliedMigrations[11].getDescription()).isEqualTo("access status");
        assertThat(appliedMigrations[11].getState()).isEqualTo(MigrationState.SUCCESS);
    }

    /** V12 (backend#137): verified accounts that exist already start their trial; others and profiles do not. */
    @Test
    void startsTheTrialOfExistingVerifiedAccounts() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .target("11")
                .load()
                .migrate();
        JdbcTemplate before = new JdbcTemplate(dataSource);
        before.update("INSERT INTO app_user (email, display_name, role, email_verified) VALUES"
                + " ('verified@example.org', NULL, 'USER', TRUE), ('unverified@example.org', NULL, 'USER', FALSE),"
                + " (NULL, 'Anna', 'USER', FALSE)");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .load()
                .migrate();

        JdbcTemplate after = new JdbcTemplate(dataSource);
        assertThat(after.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = 'verified@example.org'"
                + " AND trial_started_on = CURRENT_DATE", Integer.class)).isEqualTo(1);
        assertThat(after.queryForList("SELECT trial_started_on FROM app_user"
                + " WHERE email = 'unverified@example.org' OR display_name = 'Anna'", Object.class))
                .containsExactly(null, null);
    }

    /** V11 (backend#127): existing one-time tasks in exactly one log count as further work of that log. */
    @Test
    void assignsExistingOneTimeTasksInExactlyOneLogToThatLog() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .target("10")
                .load()
                .migrate();
        JdbcTemplate before = new JdbcTemplate(dataSource);
        Long userId = before.queryForObject("SELECT id FROM app_user WHERE role = 'ADMIN'", Long.class);
        before.update("INSERT INTO vehicle (id, name, type, model_year, first_registration_date, user_id)"
                + " VALUES (1, 'Bike', 'MOTORCYCLE', 2020, DATE '2020-01-01', ?)", userId);
        before.update("INSERT INTO maintenance_log (id, vehicle_id, performed_at, mileage_at_performed)"
                + " VALUES (10, 1, DATE '2026-01-01', 100), (11, 1, DATE '2026-02-01', 200)");
        // 1: further work of log 10; 2: one-time, in two logs; 3: one-time, in no log; 4: recurring, in one log.
        before.update("INSERT INTO maintenance_task (id, vehicle_id, name, one_time, interval_km) VALUES"
                + " (1, 1, 'Spiegel', TRUE, NULL), (2, 1, 'Zweimal', TRUE, NULL), (3, 1, 'Geplant', TRUE, NULL),"
                + " (4, 1, 'Kette', FALSE, 1000)");
        before.update("INSERT INTO maintenance_log_task (log_id, task_id) VALUES (10, 1), (10, 2), (11, 2), (11, 4)");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .load()
                .migrate();

        List<Map<String, Object>> tasks = new JdbcTemplate(dataSource)
                .queryForList("SELECT id, created_by_log_id FROM maintenance_task ORDER BY id");
        assertThat(tasks).extracting(row -> row.get("CREATED_BY_LOG_ID"))
                .containsExactly(10L, null, null, null);
    }

    @Test
    void createsAllExpectedTables() {
        List<String> tableNames = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                        String.class)
                .stream()
                .map(String::toLowerCase)
                .toList();

        assertThat(tableNames).contains("vehicle", "maintenance_task", "maintenance_log", "maintenance_log_task",
                "app_user", "spring_session", "spring_session_attributes", "user_token", "mail_quota",
                "maintenance_log_material", "vehicle_specification", "measurement_valve", "measurement_brakes",
                "measurement_chain", "measurement_tires", "subscription");
    }

    @Test
    void defaultsAdminCurrencyToEur() {
        String currency = jdbcTemplate.queryForObject(
                "SELECT currency FROM app_user WHERE role = 'ADMIN'", String.class);

        assertThat(currency).isEqualTo("EUR");
    }

    @Test
    void createsVerifiedInitialAdminFromPlaceholder() {
        Map<String, Object> admin = jdbcTemplate.queryForMap(
                "SELECT email, active, email_verified, language FROM app_user WHERE role = 'ADMIN'");

        assertThat(admin.get("EMAIL")).isEqualTo("admin@localhost");
        assertThat(admin.get("ACTIVE")).isEqualTo(true);
        assertThat(admin.get("EMAIL_VERIFIED")).isEqualTo(true);
        assertThat(admin.get("LANGUAGE")).isEqualTo("de");
    }
}
