package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MeasurementTires;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementTiresRepository extends JpaRepository<MeasurementTires, Long> {

    Optional<MeasurementTires> findByLog_Id(Long logId);

    List<MeasurementTires> findAllByLog_Vehicle_Id(Long vehicleId);

    void deleteAllByLog_Id(Long logId);
}
