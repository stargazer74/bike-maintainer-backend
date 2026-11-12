package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationMail;
import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.EarlyEndProperties;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class CancellationNoticeComponentImpl implements ICancellationNoticeComponent {

    static final String METRIC = "bm.subscription.cancellation";

    private final AppUserRepository appUserRepository;
    private final IAccountMailComponent accountMailComponent;
    private final EarlyEndProperties earlyEndProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public CancellationNoticeComponentImpl(AppUserRepository appUserRepository,
                                           IAccountMailComponent accountMailComponent,
                                           EarlyEndProperties earlyEndProperties, Clock clock,
                                           MeterRegistry meterRegistry) {
        this.appUserRepository = appUserRepository;
        this.accountMailComponent = accountMailComponent;
        this.earlyEndProperties = earlyEndProperties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        for (String term : List.of("first_year", "after_first_year", "unknown")) {
            meterRegistry.counter(METRIC, "term", term);
        }
    }

    @Override
    public void cancellationReceived(AppUser owner, Subscription subscription, ProviderSubscription current) {
        if (current.startedAt() == null || current.currentPeriodStart() == null
                || current.currentPeriodEnd() == null) {
            // Polar always reports these; without them the term cannot be judged, so the operator has to look.
            count("unknown");
            log.warn("Cancellation of subscription {} of user {} without period data, not judged", current.id(),
                    owner.getId());
            return;
        }
        if (!renewedAtLeastOnce(current)) {
            count("first_year");
            log.info("Subscription {} of user {} cancelled in the first year, ends with its term", current.id(),
                    owner.getId());
            return;
        }
        count("after_first_year");
        if (subscription.getEarlyEndState() != null) {
            log.info("Subscription {} of user {} cancelled again, early end already {}", current.id(), owner.getId(),
                    subscription.getEarlyEndState());
            return;
        }
        LocalDate receivedOn = day(current.canceledAt() != null ? current.canceledAt() : OffsetDateTime.now(clock));
        LocalDate endsOn = receivedOn.plusMonths(1);
        LocalDate periodStart = day(current.currentPeriodStart());
        LocalDate paidUntil = day(current.currentPeriodEnd());
        if (!endsOn.isBefore(paidUntil)) {
            log.info("Subscription {} of user {} cancelled after the first year, paid period ends within the month",
                    current.id(), owner.getId());
            return;
        }
        CancellationNotice notice = new CancellationNotice(owner.getId(), current.id(), receivedOn, endsOn,
                paidUntil, refund(current.amount(), periodStart, endsOn, paidUntil), current.currency());
        CancellationMail mail = CancellationMail.MANUAL;
        if (earlyEndProperties.enabled()) {
            subscription.setEarlyEndReceivedOn(receivedOn);
            subscription.setEarlyEndOn(endsOn);
            subscription.setEarlyEndPeriodStart(periodStart);
            subscription.setEarlyEndPeriodEnd(paidUntil);
            subscription.setEarlyEndState(EarlyEndState.SCHEDULED);
            subscription.setEarlyEndAttempts(0);
            subscription.setEarlyEndOrderId(null);
            subscription.setEarlyEndRefundedBefore(null);
            meterRegistry.counter(EarlyEndComponentImpl.METRIC, "result", "scheduled").increment();
            mail = CancellationMail.SCHEDULED;
        }
        List<AppUser> admins = appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN);
        for (AppUser admin : admins) {
            accountMailComponent.sendCancellationNotice(admin, notice, mail, null);
        }
        log.info("Subscription {} of user {} cancelled after the first year, ends on {} ({}); {} admins notified",
                current.id(), owner.getId(), endsOn, mail, admins.size());
    }

    @Override
    public void cancellationWithdrawn(Subscription subscription) {
        if (subscription.getEarlyEndState() != EarlyEndState.SCHEDULED) {
            return;
        }
        subscription.setEarlyEndReceivedOn(null);
        subscription.setEarlyEndOn(null);
        subscription.setEarlyEndPeriodStart(null);
        subscription.setEarlyEndPeriodEnd(null);
        subscription.setEarlyEndState(null);
        subscription.setEarlyEndAttempts(0);
        meterRegistry.counter(EarlyEndComponentImpl.METRIC, "result", "withdrawn").increment();
        log.info("Cancellation of subscription {} taken back, early end dropped", subscription.getExternalId());
    }

    /** A renewal starts a new period, so the current one begins later than the subscription (a day of slack). */
    private static boolean renewedAtLeastOnce(ProviderSubscription subscription) {
        return subscription.currentPeriodStart().isAfter(subscription.startedAt().plusDays(1));
    }

    /** The days after {@code endsOn} up to the end of the paid period, pro rata of the price; rounded to the cent. */
    static Long refund(Long amount, LocalDate periodStart, LocalDate endsOn, LocalDate paidUntil) {
        long periodDays = ChronoUnit.DAYS.between(periodStart, paidUntil);
        if (amount == null || periodDays <= 0) {
            return null;
        }
        long remainingDays = ChronoUnit.DAYS.between(endsOn, paidUntil);
        return Math.round((double) amount * remainingDays / periodDays);
    }

    private LocalDate day(OffsetDateTime dateTime) {
        ZoneId zone = clock.getZone();
        return dateTime.atZoneSameInstant(zone).toLocalDate();
    }

    private void count(String term) {
        meterRegistry.counter(METRIC, "term", term).increment();
    }
}
