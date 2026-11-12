package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUser_Id(Long userId);

    Optional<Subscription> findByProviderAndExternalId(SubscriptionProvider provider, String externalId);

    /** Early ends still to do whose last day of access is before {@code day}, with their owner. */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.user WHERE s.earlyEndState IN :states AND s.earlyEndOn < :day "
            + "ORDER BY s.id")
    List<Subscription> findEarlyEndsDueBefore(@Param("states") Collection<EarlyEndState> states,
                                             @Param("day") LocalDate day);
}
