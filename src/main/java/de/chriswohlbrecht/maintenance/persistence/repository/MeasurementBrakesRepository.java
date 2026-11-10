package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MeasurementBrakes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementBrakesRepository extends JpaRepository<MeasurementBrakes, Long> {

    Optional<MeasurementBrakes> findByLog_Id(Long logId);

    List<MeasurementBrakes> findAllByLog_Vehicle_Id(Long vehicleId);

    void deleteAllByLog_Id(Long logId);
}
