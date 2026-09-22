package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {

    List<MaintenanceTask> findAllByVehicle_Id(Long vehicleId);

    Optional<MaintenanceTask> findByIdAndVehicle_Id(Long id, Long vehicleId);
}
