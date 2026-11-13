package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.helper.AfterCommitHelper;
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
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runs also with {@code bm.polar.early-end.enabled} off: early ends planned while it was on are finished anyway.
 * No surrounding transaction: every step is saved before the next call to the provider, and only the early-end
 * columns of the current row are changed, so a webhook in between is not overwritten.
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
    /** Saves that lose against a concurrent webhook are repeated on the current row this often. */
    static final int MAX_SAVE_ATTEMPTS = 3;

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
            carryOut(subscription.getId());
        }
        if (!due.isEmpty()) {
            log.info("Early end: handled {} subscriptions", due.size());
        }
    }

    /** The account is deleted while its early end is under way: the administrators finish the refund by hand. */
    @Override
    public void accountDeleted(Subscription subscription) {
        if (subscription.getEarlyEndState() == null || !subscription.getEarlyEndState().isOpen()) {
            return;
        }
        count("manual");
        log.warn("Early end of subscription {} handed over to the administrators: account deleted in state {}",
                subscription.getId(), subscription.getEarlyEndState());
        CancellationNotice notice = noticeOf(subscription, null, null);
        List<AppUser> admins = appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN);
        // Only if the account is really gone; the deletion may still be rolled back.
        AfterCommitHelper.afterCommit(() -> admins.forEach(admin -> accountMailComponent.sendCancellationNotice(
                admin, notice, CancellationMail.FAILED, REASON_PREFIX + "accountDeleted")));
    }

    /**
     * Each subscription on its own: a failure counts as an attempt and never stops the others. Loaded again first:
     * the cancellation may have been taken back or the account deleted since the list was read.
     */
    private void carryOut(Long id) {
        Optional<Subscription> found = subscriptionRepository.findById(id);
        if (found.isEmpty() || found.get().getEarlyEndState() == null || !found.get().getEarlyEndState().isOpen()) {
            return;
        }
        Subscription current = found.get();
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
        Subscription saved = update(subscription, current -> {
            current.setEarlyEndState(EarlyEndState.REVOKED);
            current.setEarlyEndAttempts(0);
        });
        count("revoked");
        log.info("Early end: subscription {} revoked", saved.getId());
        return saved;
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
        Subscription saved = update(subscription, current -> {
            current.setEarlyEndOrderId(order.id());
            current.setEarlyEndRefundedBefore(order.refundedAmount());
            current.setEarlyEndState(EarlyEndState.REFUNDING);
        });
        paymentProviderService.refund(order.id(), amount, saved.getExternalId());
        finished(saved, amount, order.currency());
        return null;
    }

    /**
     * A run broke off after asking for the refund: refund only if neither a refund with our marker (also a pending
     * one, which the order does not show yet) nor a higher refunded amount of the order shows it went through.
     */
    private void finishRefund(Subscription subscription) {
        Optional<ProviderOrder> found = paymentProviderService.getOrder(subscription.getEarlyEndOrderId());
        if (found.isEmpty()) {
            handOver(subscription, "noOrder", null);
            return;
        }
        ProviderOrder order = found.get();
        long marked = paymentProviderService.markedRefundAmount(order.id(), subscription.getExternalId());
        if (marked > 0) {
            finished(subscription, marked, order.currency());
            return;
        }
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
        Subscription saved = update(subscription, current -> {
            current.setEarlyEndState(EarlyEndState.REFUNDED);
            current.setEarlyEndAttempts(0);
        });
        count("refunded");
        log.info("Early end: subscription {} ended and refunded", saved.getId());
        notifyAdmins(saved, CancellationMail.DONE, null, refund, currency);
    }

    private void failed(Subscription subscription, RuntimeException e) {
        EarlyEndState state = subscription.getEarlyEndState();
        try {
            Subscription saved = update(subscription,
                    current -> current.setEarlyEndAttempts(current.getEarlyEndAttempts() + 1));
            int attempts = saved.getEarlyEndAttempts();
            log.warn("Early end of subscription {} failed in state {} (attempt {}): {}", saved.getId(), state,
                    attempts, e.getClass().getSimpleName());
            if (attempts < properties.maxAttempts()) {
                count("retry");
                return;
            }
            handOver(saved, switch (state) {
                case SCHEDULED -> "unreachableBeforeEnd";
                case REVOKED -> "unreachableBeforeRefund";
                default -> "unreachableDuringRefund";
            }, null);
        } catch (RuntimeException saveFailure) {
            log.warn("Early end of subscription {} failed ({}), saving the failure failed too: {}",
                    subscription.getId(), e.getClass().getSimpleName(), saveFailure.getClass().getSimpleName());
        }
    }

    private void handOver(Subscription subscription, String reason, Long refund) {
        Subscription saved = update(subscription, current -> current.setEarlyEndState(EarlyEndState.MANUAL));
        count("manual");
        log.warn("Early end of subscription {} handed over to the administrators: {}", saved.getId(), reason);
        notifyAdmins(saved, CancellationMail.FAILED, REASON_PREFIX + reason, refund, null);
    }

    /**
     * Applies a change of the early-end columns to the current row, never to the copy read before a call to the
     * provider: the webhook of that call (ended, paid period) may have been saved meanwhile and must not be
     * overwritten. Repeated on the newest row if it changes while saving (optimistic locking).
     */
    private Subscription update(Subscription subscription, Consumer<Subscription> change) {
        for (int attempt = 1; ; attempt++) {
            Subscription current = subscriptionRepository.findById(subscription.getId())
                    .orElseThrow(() -> new IllegalStateException("Subscription " + subscription.getId() + " is gone"));
            change.accept(current);
            try {
                return subscriptionRepository.save(current);
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private void notifyAdmins(Subscription subscription, CancellationMail mail, String reasonKey, Long refund,
                              String currency) {
        CancellationNotice notice = noticeOf(subscription, refund, currency);
        for (AppUser admin : appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)) {
            accountMailComponent.sendCancellationNotice(admin, notice, mail, reasonKey);
        }
    }

    /** The owner is only needed by id, which a lazy reference knows without loading it. */
    private static CancellationNotice noticeOf(Subscription subscription, Long refund, String currency) {
        return new CancellationNotice(subscription.getUser().getId(), subscription.getExternalId(),
                subscription.getEarlyEndReceivedOn(), subscription.getEarlyEndOn(),
                subscription.getEarlyEndPeriodEnd(), refund, currency);
    }

    private void count(String result) {
        meterRegistry.counter(METRIC, "result", result).increment();
    }
}
