package de.chriswohlbrecht.maintenance;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheInitSchemaMigrationSuccessfully() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        assertThat(appliedMigrations).hasSize(1);
        assertThat(appliedMigrations[0].getDescription()).isEqualTo("init schema");
        assertThat(appliedMigrations[0].getState()).isEqualTo(MigrationState.SUCCESS);
    }

    @Test
    void createsAllExpectedTables() {
        List<String> tableNames = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                        String.class)
                .stream()
                .map(String::toLowerCase)
                .toList();

        assertThat(tableNames).contains("vehicle", "maintenance_task", "maintenance_log", "maintenance_log_task");
    }
}
