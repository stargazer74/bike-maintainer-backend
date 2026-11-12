package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.configuration.SubscriptionProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Puts accounts into a trial or subscription state directly in the database (the payment provider comes later). */
public class SubscriptionSteps {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionProperties subscriptionProperties;

    @Autowired
    private Clock clock;

    /** Moves the start of the trial back, so yesterday was its last day. */
    @Given("the trial of {string} has ended")
    public void theTrialHasEnded(String email) {
        AppUser user = user(email);
        user.setTrialStartedOn(LocalDate.now(clock).minusDays(subscriptionProperties.trialDays()));
        appUserRepository.save(user);
    }

    @Given("{string} has a subscription paid until in {int} days")
    public void hasASubscription(String email, int days) {
        subscription(email, SubscriptionStatus.ACTIVE, LocalDate.now(clock).plusDays(days));
    }

    @Given("{string} had a subscription that ended {int} days ago")
    public void hadASubscription(String email, int days) {
        subscription(email, SubscriptionStatus.ENDED, LocalDate.now(clock).minusDays(days));
    }

    private void subscription(String email, SubscriptionStatus status, LocalDate paidUntil) {
        subscriptionRepository.save(Subscription.builder()
                .user(user(email))
                .provider(SubscriptionProvider.POLAR)
                .externalId("sub_" + UUID.randomUUID())
                .status(status)
                .paidUntil(paidUntil)
                .build());
    }

    private AppUser user(String email) {
        return appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
