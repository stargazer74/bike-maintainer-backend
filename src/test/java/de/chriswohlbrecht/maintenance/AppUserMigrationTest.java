package de.chriswohlbrecht.maintenance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migrates a fresh database up to V3, inserts pre-existing data and then applies V4, the same way the
 * migration runs against a deployed database that already contains vehicles.
 */
class AppUserMigrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void assignsExistingVehiclesToInitialAdmin() {
        migrate("3");
        jdbcTemplate.update("INSERT INTO vehicle (name, type, model_year, first_registration_date, current_mileage) "
                + "VALUES ('Old Bike', 'MOTORCYCLE', 2010, DATE '2010-05-01', 42000)");
        jdbcTemplate.update("INSERT INTO vehicle (name, type, model_year, first_registration_date, current_mileage) "
                + "VALUES ('Old Car', 'CAR', 2015, DATE '2015-03-01', 90000)");

        migrate("latest");

        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE role = 'ADMIN'", Long.class);
        assertThat(jdbcTemplate.queryForList("SELECT user_id FROM vehicle", Long.class))
                .hasSize(2)
                .containsOnly(adminId);
    }

    @Test
    void storesInitialAdminEmailTrimmedAndLowerCased() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "  Chris@Example.ORG "))
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject("SELECT email FROM app_user WHERE role = 'ADMIN'", String.class))
                .isEqualTo("chris@example.org");
    }

    @Test
    void rejectsVehicleWithoutOwnerAfterMigration() {
        migrate("latest");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO vehicle (name, type, model_year, first_registration_date, current_mileage) "
                        + "VALUES ('Orphan', 'CAR', 2020, DATE '2020-01-01', 0)"))
                .hasMessageContaining("USER_ID");
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("initialAdminEmail", "admin@localhost"))
                .target(target)
                .load()
                .migrate();
    }
}
