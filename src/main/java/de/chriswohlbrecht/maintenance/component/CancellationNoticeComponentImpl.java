package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
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
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public CancellationNoticeComponentImpl(AppUserRepository appUserRepository,
                                           IAccountMailComponent accountMailComponent, Clock clock,
                                           MeterRegistry meterRegistry) {
        this.appUserRepository = appUserRepository;
        this.accountMailComponent = accountMailComponent;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        for (String term : List.of("first_year", "after_first_year", "unknown")) {
            meterRegistry.counter(METRIC, "term", term);
        }
    }

    @Override
    public void cancellationReceived(AppUser owner, ProviderSubscription subscription) {
        if (subscription.startedAt() == null || subscription.currentPeriodStart() == null
                || subscription.currentPeriodEnd() == null) {
            // Polar always reports these; without them the term cannot be judged, so the operator has to look.
            count("unknown");
            log.warn("Cancellation of subscription {} of user {} without period data, not judged", subscription.id(),
                    owner.getId());
            return;
        }
        if (!renewedAtLeastOnce(subscription)) {
            count("first_year");
            log.info("Subscription {} of user {} cancelled in the first year, ends with its term", subscription.id(),
                    owner.getId());
            return;
        }
        count("after_first_year");
        LocalDate receivedOn = day(subscription.canceledAt() != null ? subscription.canceledAt()
                : OffsetDateTime.now(clock));
        LocalDate endsOn = receivedOn.plusMonths(1);
        LocalDate periodStart = day(subscription.currentPeriodStart());
        LocalDate paidUntil = day(subscription.currentPeriodEnd());
        if (!endsOn.isBefore(paidUntil)) {
            log.info("Subscription {} of user {} cancelled after the first year, paid period ends within the month",
                    subscription.id(), owner.getId());
            return;
        }
        CancellationNotice notice = new CancellationNotice(owner.getId(), subscription.id(), receivedOn, endsOn,
                paidUntil, refund(subscription.amount(), periodStart, endsOn, paidUntil), subscription.currency());
        List<AppUser> admins = appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN);
        admins.forEach(admin -> accountMailComponent.sendCancellationNotice(admin, notice));
        log.info("Subscription {} of user {} cancelled after the first year, ends on {}; {} admins notified",
                subscription.id(), owner.getId(), endsOn, admins.size());
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
