package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseCleanupHooks {

    @Autowired
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;

    @Autowired
    private MaintenanceLogRepository maintenanceLogRepository;

    @Autowired
    private MaintenanceTaskRepository maintenanceTaskRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Keeps only the initial admin created by migration V4; everything else is created per scenario. */
    @Before
    public void cleanDatabase() {
        maintenanceLogTaskRepository.deleteAllInBatch();
        maintenanceLogRepository.deleteAllInBatch();
        maintenanceTaskRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch(appUserRepository.findAll().stream()
                .filter(user -> user.getRole() != UserRole.ADMIN)
                .toList());
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }
}
