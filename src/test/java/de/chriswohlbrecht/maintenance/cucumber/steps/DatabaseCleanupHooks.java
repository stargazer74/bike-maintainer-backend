package de.chriswohlbrecht.maintenance.cucumber.steps;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
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

    private static final String INITIAL_ADMIN_EMAIL = "admin@localhost";

    /** Keeps only the initial admin created by migration V4; everything else is created per scenario. */
    @Before
    public void cleanDatabase() {
        maintenanceLogTaskRepository.deleteAllInBatch();
        maintenanceLogRepository.deleteAllInBatch();
        maintenanceTaskRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM subscription");
        jdbcTemplate.update("DELETE FROM device_login");
        jdbcTemplate.update("DELETE FROM user_token");
        jdbcTemplate.update("DELETE FROM mail_quota");
        // Only the admin created by migration V4 stays (email from the configuration in portal mode, no email for the
        // household profile); scenarios may promote, demote or deactivate users, so its role and state are reset too.
        appUserRepository.deleteAllInBatch(appUserRepository.findAll().stream()
                .filter(user -> !isInitialAdmin(user))
                .toList());
        jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN', active = TRUE, failed_login_attempts = 0, "
                + "locked_until = NULL, billing_customer_ref = NULL");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    private static boolean isInitialAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN
                && (user.getEmail() == null || INITIAL_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail()));
    }
}
