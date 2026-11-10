package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MeasurementChain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementChainRepository extends JpaRepository<MeasurementChain, Long> {

    Optional<MeasurementChain> findByLog_Id(Long logId);

    List<MeasurementChain> findAllByLog_Vehicle_Id(Long vehicleId);

    void deleteAllByLog_Id(Long logId);
}
