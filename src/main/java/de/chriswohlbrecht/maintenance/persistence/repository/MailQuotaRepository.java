package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.MailQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MailQuotaRepository extends JpaRepository<MailQuota, LocalDate> {

    /** Creates the counter of the day if it does not exist yet; harmless if it does (also for parallel calls). */
    @Modifying
    @Query(value = "INSERT INTO mail_quota (quota_date, sent_count) VALUES (:date, 0) "
            + "ON DUPLICATE KEY UPDATE sent_count = sent_count", nativeQuery = true)
    void createIfMissing(@Param("date") LocalDate date);

    /** Increments the counter only while it is below the limit; returns 1 if incremented, else 0. */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE mail_quota SET sent_count = sent_count + 1 "
            + "WHERE quota_date = :date AND sent_count < :limit", nativeQuery = true)
    int incrementBelowLimit(@Param("date") LocalDate date, @Param("limit") int limit);
}
