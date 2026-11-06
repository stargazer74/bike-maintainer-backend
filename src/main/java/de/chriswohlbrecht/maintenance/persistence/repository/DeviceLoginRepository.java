package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.DeviceLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceLoginRepository extends JpaRepository<DeviceLogin, Long> {

    Optional<DeviceLogin> findByTokenHash(String tokenHash);

    Optional<DeviceLogin> findByPreviousTokenHash(String tokenHash);

    List<DeviceLogin> findAllByUser_IdOrderByLastUsedAtDesc(Long userId);

    Optional<DeviceLogin> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Replaces the token unless another request did so first; the condition is checked by the database, so of two
     * parallel exchanges with the same token only one gets 1.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE DeviceLogin d SET d.previousTokenHash = :oldHash, d.tokenHash = :newHash, d.lastUsedAt = :now "
            + "WHERE d.id = :id AND d.tokenHash = :oldHash")
    int rotate(@Param("id") Long id, @Param("oldHash") String oldHash, @Param("newHash") String newHash,
               @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeviceLogin d WHERE d.user.id = :userId")
    int deleteAllByUser_Id(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeviceLogin d WHERE d.lastUsedAt < :cutoff")
    int deleteUnusedSince(@Param("cutoff") LocalDateTime cutoff);
}
