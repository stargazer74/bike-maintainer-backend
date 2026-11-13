package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real SQL against H2 (MariaDB mode) with only the JPA slice. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final List<SubscriptionStatus> RUNNING =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private AppUserRepository appUserRepository;

    /** The nightly re-sync asks for running subscriptions whose paid period is over or unknown. */
    @Test
    void runningSubscriptionsWithAnOverduePaidPeriodAreFound() {
        AppUser user = user("resync@example.org");
        save(user, "sub_overdue", SubscriptionStatus.ACTIVE, TODAY.minusDays(1));
        save(user, "sub_unknown", SubscriptionStatus.PAST_DUE, null);
        save(user, "sub_paid", SubscriptionStatus.ACTIVE, TODAY);
        save(user, "sub_cancelled", SubscriptionStatus.CANCELED, TODAY.minusDays(10));
        save(user, "sub_ended", SubscriptionStatus.ENDED, TODAY.minusDays(10));

        assertThat(subscriptionRepository.findRunningUnpaidSince(SubscriptionProvider.POLAR, RUNNING, TODAY))
                .containsExactly("sub_overdue", "sub_unknown");
    }

    @Test
    void subscriptionsOfManyUsersAreLoadedAtOnce() {
        AppUser first = user("first@example.org");
        AppUser second = user("second@example.org");
        AppUser other = user("other@example.org");
        save(first, "sub_1", SubscriptionStatus.ACTIVE, TODAY);
        save(second, "sub_2", SubscriptionStatus.ENDED, TODAY);
        save(other, "sub_3", SubscriptionStatus.ACTIVE, TODAY);

        assertThat(subscriptionRepository.findAllByUser_IdIn(List.of(first.getId(), second.getId())))
                .extracting(Subscription::getExternalId)
                .containsExactlyInAnyOrder("sub_1", "sub_2");
    }

    /**
     * A copy read before a webhook saved the row cannot overwrite it any more (backend review, finding 1). Without a
     * test transaction, so each save is committed on its own like in the nightly job.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aStaleCopyCannotOverwriteANewerSave() {
        AppUser user = user("stale@example.org");
        Subscription created = save(user, "sub_stale", SubscriptionStatus.CANCELED, TODAY.plusDays(300));
        try {
            Subscription stale = subscriptionRepository.findById(created.getId()).orElseThrow();
            Subscription webhook = subscriptionRepository.findById(created.getId()).orElseThrow();
            webhook.setStatus(SubscriptionStatus.ENDED);
            webhook.setPaidUntil(TODAY);
            subscriptionRepository.save(webhook);

            stale.setPaidUntil(TODAY.plusDays(300));
            assertThatThrownBy(() -> subscriptionRepository.save(stale))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(subscriptionRepository.findById(created.getId())).get()
                    .extracting(Subscription::getStatus).isEqualTo(SubscriptionStatus.ENDED);
        } finally {
            subscriptionRepository.deleteAll(subscriptionRepository.findAllByUser_Id(user.getId()));
            appUserRepository.deleteById(user.getId());
        }
    }

    private AppUser user(String email) {
        return appUserRepository.save(AppUser.builder().email(email).role(UserRole.USER).emailVerified(true).build());
    }

    private Subscription save(AppUser user, String externalId, SubscriptionStatus status, LocalDate paidUntil) {
        return subscriptionRepository.save(Subscription.builder().user(user).provider(SubscriptionProvider.POLAR)
                .externalId(externalId).status(status).paidUntil(paidUntil).build());
    }
}
