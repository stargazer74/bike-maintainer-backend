package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findAllByUser_Id(Long userId);

    Optional<Vehicle> findByIdAndUser_Id(Long id, Long userId);
}
