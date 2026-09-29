package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseCleanupHooks {

    @Autowired
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;

    @Autowired
    private MaintenanceLogRepository maintenanceLogRepository;

    @Autowired
    private MaintenanceTaskRepository maintenanceTaskRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Before
    public void cleanDatabase() {
        maintenanceLogTaskRepository.deleteAllInBatch();
        maintenanceLogRepository.deleteAllInBatch();
        maintenanceTaskRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
    }
}
