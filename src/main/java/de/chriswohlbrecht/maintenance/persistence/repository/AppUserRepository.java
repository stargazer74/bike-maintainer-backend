package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByBillingCustomerRef(String billingCustomerRef);

    /** Sets the reference only if the account has none yet: of two checkouts at the same time, the first one wins. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.billingCustomerRef = :ref WHERE u.id = :id AND u.billingCustomerRef IS NULL")
    int setBillingCustomerRefIfMissing(@Param("id") Long id, @Param("ref") String ref);

    @Query("SELECT u.billingCustomerRef FROM AppUser u WHERE u.id = :id")
    Optional<String> findBillingCustomerRefById(@Param("id") Long id);

    /** Locks the row until the end of the transaction, e.g. so a login cannot interleave with a deletion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> findLockedById(@Param("id") Long id);

    /** Only this column, so a stale copy of the account never overwrites e.g. a password changed in the meantime. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.deletionNoticeSentAt = :sentAt WHERE u.id = :id")
    int setDeletionNoticeSentAt(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    long countByRoleAndActiveTrue(UserRole role);

    List<AppUser> findAllByRoleAndActiveTrue(UserRole role);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, Long id);

    /** Registered accounts (with email) of the given role whose email was never verified before {@code cutoff}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AppUser u WHERE u.emailVerified = false AND u.email IS NOT NULL AND u.role = :role "
            + "AND u.createdAt < :cutoff")
    int deleteUnverifiedCreatedBefore(@Param("role") UserRole role, @Param("cutoff") LocalDateTime cutoff);

    /**
     * Verified accounts of the given role that may be due for deletion: trial started before {@code trialStartedBefore}
     * (access can only have ended after the trial) or with a pending deletion notice.
     */
    @Query("SELECT u FROM AppUser u WHERE u.role = :role AND u.email IS NOT NULL AND u.emailVerified = true "
            + "AND (u.trialStartedOn < :trialStartedBefore OR u.deletionNoticeSentAt IS NOT NULL) ORDER BY u.id")
    List<AppUser> findRetentionCandidates(@Param("role") UserRole role,
                                          @Param("trialStartedBefore") LocalDate trialStartedBefore);
}
