package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceLogMaterialRepository extends JpaRepository<MaintenanceLogMaterial, Long> {

    List<MaintenanceLogMaterial> findAllByLog_IdOrderByPosition(Long logId);

    List<MaintenanceLogMaterial> findAllByLog_Vehicle_Id(Long vehicleId);

    void deleteAllByLog_Id(Long logId);

    void deleteAllByLog_Vehicle_Id(Long vehicleId);
}
