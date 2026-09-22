package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTaskId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceLogTaskRepository extends JpaRepository<MaintenanceLogTask, MaintenanceLogTaskId> {

    List<MaintenanceLogTask> findAllByLog_Id(Long logId);

    void deleteAllByLog_Id(Long logId);
}
