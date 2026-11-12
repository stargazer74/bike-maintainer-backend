package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationMail;
import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.configuration.EarlyEndProperties;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.service.IPaymentProviderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Runs also with {@code bm.polar.early-end.enabled} off: early ends planned while it was on are finished anyway.
 * No surrounding transaction: every step is saved before the next call to the provider.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class EarlyEndComponentImpl implements IEarlyEndComponent {

    static final String METRIC = "bm.subscription.early_end";
    private static final List<String> RESULTS =
            List.of("scheduled", "withdrawn", "revoked", "refunded", "manual", "retry");
    private static final List<EarlyEndState> OPEN_STATES =
            List.of(EarlyEndState.SCHEDULED, EarlyEndState.REVOKED, EarlyEndState.REFUNDING);
    private static final String REASON_PREFIX = "mail.earlyEnd.reason.";

    private final SubscriptionRepository subscriptionRepository;
    private final AppUserRepository appUserRepository;
    private final IPaymentProviderService paymentProviderService;
    private final IAccountMailComponent accountMailComponent;
    private final EarlyEndProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public EarlyEndComponentImpl(SubscriptionRepository subscriptionRepository, AppUserRepository appUserRepository,
                                 IPaymentProviderService paymentProviderService,
                                 IAccountMailComponent accountMailComponent, EarlyEndProperties properties,
                                 Clock clock, MeterRegistry meterRegistry) {
        this.subscriptionRepository = subscriptionRepository;
        this.appUserRepository = appUserRepository;
        this.paymentProviderService = paymentProviderService;
        this.accountMailComponent = accountMailComponent;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        RESULTS.forEach(result -> meterRegistry.counter(METRIC, "result", result));
    }

    @Override
    @Scheduled(cron = "${bm.cleanup.cron}")
    public void carryOutDueEarlyEnds() {
        List<Subscription> due = subscriptionRepository.findEarlyEndsDueBefore(OPEN_STATES, LocalDate.now(clock));
        for (Subscription subscription : due) {
            carryOut(subscription);
        }
        if (!due.isEmpty()) {
            log.info("Early end: handled {} subscriptions", due.size());
        }
    }

    /** Each subscription on its own: a failure counts as an attempt and never stops the others. */
    private void carryOut(Subscription subscription) {
        Subscription current = subscription;
        try {
            if (current.getEarlyEndState() == EarlyEndState.SCHEDULED) {
                current = revoke(current);
            }
            if (current != null && current.getEarlyEndState() == EarlyEndState.REVOKED) {
                current = startRefund(current);
            }
            if (current != null && current.getEarlyEndState() == EarlyEndState.REFUNDING) {
                finishRefund(current);
            }
        } catch (RuntimeException e) {
            // Most likely the provider; logged without personal data, retried on the next run.
            failed(current, e);
        }
    }

    /** @return the saved subscription, or null if it was handed over to the administrators */
    private Subscription revoke(Subscription subscription) {
        if (!paymentProviderService.revokeSubscription(subscription.getExternalId())) {
            handOver(subscription, "alreadyEnded", null);
            return null;
        }
        subscription.setEarlyEndState(EarlyEndState.REVOKED);
        subscription.setEarlyEndAttempts(0);
        count("revoked");
        log.info("Early end: subscription {} revoked", subscription.getId());
        return subscriptionRepository.save(subscription);
    }

    /** Remembers the order and what was refunded before, then refunds; null if handed over or finished. */
    private Subscription startRefund(Subscription subscription) {
        Optional<ProviderOrder> found = paymentProviderService.findLastPeriodOrder(subscription.getExternalId());
        if (found.isEmpty()) {
            handOver(subscription, "noOrder", null);
            return null;
        }
        ProviderOrder order = found.get();
        long amount = amountFor(subscription, order);
        if (amount <= 0) {
            finished(subscription, 0, order.currency());
            return null;
        }
        subscription.setEarlyEndOrderId(order.id());
        subscription.setEarlyEndRefundedBefore(order.refundedAmount());
        subscription.setEarlyEndState(EarlyEndState.REFUNDING);
        Subscription saved = subscriptionRepository.save(subscription);
        paymentProviderService.refund(order.id(), amount, saved.getExternalId());
        finished(saved, amount, order.currency());
        return null;
    }

    /** A run broke off after asking for the refund: refund only if the order shows nothing new since. */
    private void finishRefund(Subscription subscription) {
        Optional<ProviderOrder> found = paymentProviderService.getOrder(subscription.getEarlyEndOrderId());
        if (found.isEmpty()) {
            handOver(subscription, "noOrder", null);
            return;
        }
        ProviderOrder order = found.get();
        long refundedBefore = subscription.getEarlyEndRefundedBefore() == null
                ? 0 : subscription.getEarlyEndRefundedBefore();
        if (order.refundedAmount() > refundedBefore) {
            finished(subscription, order.refundedAmount() - refundedBefore, order.currency());
            return;
        }
        long amount = amountFor(subscription, order);
        if (amount > 0) {
            paymentProviderService.refund(order.id(), amount, subscription.getExternalId());
        }
        finished(subscription, Math.max(amount, 0), order.currency());
    }

    /** The days after the end, pro rata of the order's net amount; never more than can still be refunded. */
    static long amountFor(Subscription subscription, ProviderOrder order) {
        Long prorated = CancellationNoticeComponentImpl.refund(order.netAmount(),
                subscription.getEarlyEndPeriodStart(), subscription.getEarlyEndOn(),
                subscription.getEarlyEndPeriodEnd());
        return prorated == null ? 0 : Math.min(prorated, order.refundableAmount());
    }

    private void finished(Subscription subscription, long refund, String currency) {
        subscription.setEarlyEndState(EarlyEndState.REFUNDED);
        subscription.setEarlyEndAttempts(0);
        Subscription saved = subscriptionRepository.save(subscription);
        count("refunded");
        log.info("Early end: subscription {} ended and refunded", saved.getId());
        notifyAdmins(saved, CancellationMail.DONE, null, refund, currency);
    }

    private void failed(Subscription subscription, RuntimeException e) {
        EarlyEndState state = subscription.getEarlyEndState();
        int attempts = subscription.getEarlyEndAttempts() + 1;
        subscription.setEarlyEndAttempts(attempts);
        log.warn("Early end of subscription {} failed in state {} (attempt {}): {}", subscription.getId(), state,
                attempts, e.getClass().getSimpleName());
        try {
            if (attempts < properties.maxAttempts()) {
                subscriptionRepository.save(subscription);
                count("retry");
                return;
            }
            handOver(subscription, switch (state) {
                case SCHEDULED -> "unreachableBeforeEnd";
                case REVOKED -> "unreachableBeforeRefund";
                default -> "unreachableDuringRefund";
            }, null);
        } catch (RuntimeException saveFailure) {
            log.warn("Early end of subscription {}: saving the failure failed: {}", subscription.getId(),
                    saveFailure.getClass().getSimpleName());
        }
    }

    private void handOver(Subscription subscription, String reason, Long refund) {
        subscription.setEarlyEndState(EarlyEndState.MANUAL);
        Subscription saved = subscriptionRepository.save(subscription);
        count("manual");
        log.warn("Early end of subscription {} handed over to the administrators: {}", saved.getId(), reason);
        notifyAdmins(saved, CancellationMail.FAILED, REASON_PREFIX + reason, refund, null);
    }

    private void notifyAdmins(Subscription subscription, CancellationMail mail, String reasonKey, Long refund,
                              String currency) {
        AppUser owner = subscription.getUser();
        CancellationNotice notice = new CancellationNotice(owner.getId(), subscription.getExternalId(),
                subscription.getEarlyEndReceivedOn(), subscription.getEarlyEndOn(),
                subscription.getEarlyEndPeriodEnd(), refund, currency);
        for (AppUser admin : appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)) {
            accountMailComponent.sendCancellationNotice(admin, notice, mail, reasonKey);
        }
    }

    private void count(String result) {
        meterRegistry.counter(METRIC, "result", result).increment();
    }
}
