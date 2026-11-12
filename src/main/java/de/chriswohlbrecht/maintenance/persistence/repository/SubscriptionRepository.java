package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUser_Id(Long userId);
}
