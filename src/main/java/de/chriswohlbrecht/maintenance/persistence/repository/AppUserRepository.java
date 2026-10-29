package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, Long id);

    /** Registered accounts (with email) of the given role whose email was never verified before {@code cutoff}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AppUser u WHERE u.emailVerified = false AND u.email IS NOT NULL AND u.role = :role "
            + "AND u.createdAt < :cutoff")
    int deleteUnverifiedCreatedBefore(@Param("role") UserRole role, @Param("cutoff") LocalDateTime cutoff);
}
