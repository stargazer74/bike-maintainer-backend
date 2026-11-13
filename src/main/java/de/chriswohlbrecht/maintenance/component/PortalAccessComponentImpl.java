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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Portal mode: a free trial from the day the email address was verified, then a paid subscription; without either
 * the account is read-only. Administrators run the service and are never restricted.
 */
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalAccessComponentImpl implements IAccessComponent {

    private static final String WRITE_GRANTED = PortalAccessComponentImpl.class.getName() + ".writeGranted";
    /**
     * How long a subscription the provider reports running still counts after its paid period, and how long a
     * confirmation by the provider (webhook or nightly re-sync) is trusted for it. Covers the delay of the renewal
     * webhook; after a lost webhook access ends then instead of never.
     */
    static final int RUNNING_TOLERANCE_DAYS = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final SubscriptionProperties subscriptionProperties;
    private final Clock clock;

    @Override
    public Access accessOf(AppUser user) {
        return accessOf(user, subscriptionRepository.findAllByUser_Id(user.getId()));
    }

    @Override
    public Map<Long, Access> accessOf(List<AppUser> users) {
        Map<Long, List<Subscription>> byUser = subscriptionRepository
                .findAllByUser_IdIn(users.stream().map(AppUser::getId).toList()).stream()
                .collect(Collectors.groupingBy(subscription -> subscription.getUser().getId()));
        Map<Long, Access> access = new HashMap<>();
        for (AppUser user : users) {
            access.put(user.getId(), accessOf(user, byUser.getOrDefault(user.getId(), List.of())));
        }
        return access;
    }

    /**
     * A subscription counts while its paid period lasts (e.g. after a cancellation), and while the provider reports it
     * running (also after a failed payment, until the provider gives up) – the latter only as long as that is
     * confirmed: within a few days after the paid period, or by a recent sync. It wins over the trial.
     */
    private Access accessOf(AppUser user, List<Subscription> subscriptions) {
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
        boolean running = subscriptions.stream().anyMatch(subscription -> isConfirmedRunning(subscription, today));
        if (running || paidUntil != null && !paidUntil.isBefore(today)) {
            return new Access(AccessStatus.ACTIVE, paidUntil, true);
        }
        LocalDate trialEnd = trialEnd(user);
        if (trialEnd != null && !trialEnd.isBefore(today)) {
            return new Access(AccessStatus.TRIAL, trialEnd, hasSubscription);
        }
        return new Access(AccessStatus.EXPIRED, latest(paidUntil, trialEnd), hasSubscription);
    }

    /** Remembers a granted write access in the request, see {@link IAccessComponent#requireWriteAccess()}. */
    @Override
    public Access requireWriteAccess() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(WRITE_GRANTED, RequestAttributes.SCOPE_REQUEST)
                instanceof Access granted) {
            return granted;
        }
        Access access = accessOf(currentUserComponent.getCurrentUser());
        if (!access.canWrite()) {
            throw new SubscriptionRequiredException();
        }
        if (request != null) {
            request.setAttribute(WRITE_GRANTED, access, RequestAttributes.SCOPE_REQUEST);
        }
        return access;
    }

    private boolean isConfirmedRunning(Subscription subscription, LocalDate today) {
        if (!subscription.getStatus().isRunning()) {
            return false;
        }
        LocalDate oldest = today.minusDays(RUNNING_TOLERANCE_DAYS);
        LocalDate paidUntil = subscription.getPaidUntil();
        LocalDateTime syncedAt = subscription.getSyncedAt();
        return paidUntil != null && !paidUntil.isBefore(oldest)
                || syncedAt != null && !syncedAt.toLocalDate().isBefore(oldest);
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
