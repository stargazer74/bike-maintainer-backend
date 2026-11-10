package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MeasurementValve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasurementValveRepository extends JpaRepository<MeasurementValve, Long> {

    List<MeasurementValve> findAllByLog_Id(Long logId);

    List<MeasurementValve> findAllByLog_Vehicle_Id(Long vehicleId);

    void deleteAllByLog_Id(Long logId);
}
