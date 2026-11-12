package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.SubscriptionProperties;
import de.chriswohlbrecht.maintenance.exception.SubscriptionRequiredException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Portal mode: a free trial from the day the email address was verified, then a paid subscription; without either
 * the account is read-only. Administrators run the service and are never restricted.
 */
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalAccessComponentImpl implements IAccessComponent {

    private final SubscriptionRepository subscriptionRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final SubscriptionProperties subscriptionProperties;
    private final Clock clock;

    /**
     * A subscription counts while the provider reports it running (also after a failed payment, until the provider
     * gives up) or while its paid period lasts (e.g. after a cancellation). It wins over the trial.
     */
    @Override
    public Access accessOf(AppUser user) {
        List<Subscription> subscriptions = subscriptionRepository.findAllByUser_Id(user.getId());
        boolean hasSubscription = !subscriptions.isEmpty();
        if (user.getRole() == UserRole.ADMIN) {
            return Access.full(hasSubscription);
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate paidUntil = subscriptions.stream()
                .map(Subscription::getPaidUntil)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean running = subscriptions.stream().anyMatch(subscription -> subscription.getStatus().isRunning());
        if (running || paidUntil != null && !paidUntil.isBefore(today)) {
            return new Access(AccessStatus.ACTIVE, paidUntil, true);
        }
        LocalDate trialEnd = trialEnd(user);
        if (trialEnd != null && !trialEnd.isBefore(today)) {
            return new Access(AccessStatus.TRIAL, trialEnd, hasSubscription);
        }
        return new Access(AccessStatus.EXPIRED, latest(paidUntil, trialEnd), hasSubscription);
    }

    @Override
    public void requireWriteAccess() {
        if (!accessOf(currentUserComponent.getCurrentUser()).canWrite()) {
            throw new SubscriptionRequiredException();
        }
    }

    private LocalDate trialEnd(AppUser user) {
        LocalDate start = user.getTrialStartedOn();
        return start == null ? null : start.plusDays(subscriptionProperties.trialDays() - 1L);
    }

    private static LocalDate latest(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }
}
