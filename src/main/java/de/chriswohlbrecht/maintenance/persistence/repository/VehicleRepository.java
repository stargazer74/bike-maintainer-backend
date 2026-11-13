package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findAllByUser_Id(Long userId);

    Optional<Vehicle> findByIdAndUser_Id(Long id, Long userId);

    /** Like {@link #findByIdAndUser_Id}, but locks the row until the end of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.user.id = :userId")
    Optional<Vehicle> findLockedByIdAndUser_Id(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUser_Id(Long userId);

    long countByUser_Id(Long userId);
}
