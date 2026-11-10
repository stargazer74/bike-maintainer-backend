package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleSpecificationRepository extends JpaRepository<VehicleSpecification, Long> {
}
