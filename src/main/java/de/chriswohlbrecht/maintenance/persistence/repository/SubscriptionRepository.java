package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUser_Id(Long userId);

    List<Subscription> findAllByUser_IdIn(Collection<Long> userIds);

    /** Ids at the provider of the subscriptions it reports running although their paid period is over (or unknown). */
    @Query("SELECT s.externalId FROM Subscription s WHERE s.provider = :provider AND s.status IN :statuses "
            + "AND (s.paidUntil IS NULL OR s.paidUntil < :day) ORDER BY s.id")
    List<String> findRunningUnpaidSince(@Param("provider") SubscriptionProvider provider,
                                        @Param("statuses") Collection<SubscriptionStatus> statuses,
                                        @Param("day") LocalDate day);

    Optional<Subscription> findByProviderAndExternalId(SubscriptionProvider provider, String externalId);

    /** Early ends still to do whose last day of access is before {@code day}, with their owner. */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.user WHERE s.earlyEndState IN :states AND s.earlyEndOn < :day "
            + "ORDER BY s.id")
    List<Subscription> findEarlyEndsDueBefore(@Param("states") Collection<EarlyEndState> states,
                                             @Param("day") LocalDate day);
}
