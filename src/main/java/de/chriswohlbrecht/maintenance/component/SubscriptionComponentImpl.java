package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
import de.chriswohlbrecht.maintenance.exception.AlreadySubscribedException;
import de.chriswohlbrecht.maintenance.exception.InvalidWebhookException;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.service.IPaymentProviderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription via Paddle. The provider is the source of truth: a webhook is only a trigger to fetch the current
 * state of the subscription, so duplicates and events out of order do no harm. Calls to the provider (up to several
 * seconds) never run inside a database transaction; the transactions around them are short.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class SubscriptionComponentImpl implements ISubscriptionComponent {

    static final String CHECKOUT_METRIC = "bm.subscription.checkout";
    static final String WEBHOOK_METRIC = "bm.subscription.webhook";
    static final String RESYNC_METRIC = "bm.subscription.resync";
    /** Parallel webhooks of one subscription: the one that lost is stored again on the current row. */
    static final int MAX_STORE_ATTEMPTS = 3;
    private static final String SYNCED = "synced";
    private static final String UNKNOWN_CUSTOMER = "unknown_customer";
    private static final List<SubscriptionStatus> RUNNING =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);
    private static final String SUBSCRIPTION_EVENT_PREFIX = "subscription.";

    private final ICurrentUserComponent currentUserComponent;
    private final IAccessComponent accessComponent;
    private final IPaymentProviderService paymentProviderService;
    private final ICancellationNoticeComponent cancellationNoticeComponent;
    private final IEarlyEndComponent earlyEndComponent;
    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaddleProperties paddleProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final TransactionOperations transactionOperations;

    @SuppressWarnings("java:S107") // user, access, provider, persistence, configuration and monitoring
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public SubscriptionComponentImpl(ICurrentUserComponent currentUserComponent, IAccessComponent accessComponent,
                                     IPaymentProviderService paymentProviderService,
                                     ICancellationNoticeComponent cancellationNoticeComponent,
                                     IEarlyEndComponent earlyEndComponent, AppUserRepository appUserRepository,
                                     SubscriptionRepository subscriptionRepository, PaddleProperties paddleProperties,
                                     JsonMapper jsonMapper, Clock clock, MeterRegistry meterRegistry,
                                     TransactionOperations transactionOperations) {
        this.currentUserComponent = currentUserComponent;
        this.accessComponent = accessComponent;
        this.paymentProviderService = paymentProviderService;
        this.cancellationNoticeComponent = cancellationNoticeComponent;
        this.earlyEndComponent = earlyEndComponent;
        this.appUserRepository = appUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paddleProperties = paddleProperties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.transactionOperations = transactionOperations;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        for (String result : List.of("started", "already_subscribed", "failure")) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", result);
        }
        for (String result : List.of(SYNCED, "ignored", UNKNOWN_CUSTOMER, "invalid_signature", "failure")) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", result);
        }
        for (String result : List.of(SYNCED, UNKNOWN_CUSTOMER, "failure")) {
            meterRegistry.counter(RESYNC_METRIC, "result", result);
        }
    }

    /** The random reference is created on the first checkout and kept, so all subscriptions land on one customer. */
    @Override
    public String startCheckout() {
        AppUser user = currentUserComponent.getCurrentUser();
        if (accessComponent.accessOf(user).status() == AccessStatus.ACTIVE) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", "already_subscribed").increment();
            throw new AlreadySubscribedException();
        }
        String customerRef = customerRefOf(user);
        try {
            String url = paymentProviderService.createCheckout(customerRef, user.getEmail());
            meterRegistry.counter(CHECKOUT_METRIC, "result", "started").increment();
            log.info("User {} started a checkout", user.getId());
            return url;
        } catch (PaymentProviderUnavailableException e) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", "failure").increment();
            throw e;
        }
    }

    /** The portal belongs to the provider's customer; it is opened through the newest subscription of the account. */
    @Override
    public Optional<String> openCustomerPortal() {
        AppUser user = currentUserComponent.getCurrentUser();
        return subscriptionRepository.findAllByUser_Id(user.getId()).stream()
                .max(Comparator.comparing(Subscription::getId))
                .flatMap(newest -> paymentProviderService.createCustomerPortal(newest.getExternalId()));
    }

    @Override
    public void handleWebhook(String signature, String body) {
        try {
            WebhookSignatureHelper.verify(paddleProperties.webhookSecret(), signature, body, clock.instant());
        } catch (RuntimeException e) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", "invalid_signature").increment();
            throw e;
        }
        JsonNode event;
        try {
            event = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Webhook body is not JSON");
        }
        String type = event.path("event_type").asString("");
        String subscriptionId = event.path("data").path("id").asString("");
        if (!type.startsWith(SUBSCRIPTION_EVENT_PREFIX)) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", "ignored").increment();
            return;
        }
        if (subscriptionId.isBlank()) {
            throw new InvalidWebhookException("Subscription event without data.id");
        }
        String result;
        try {
            result = sync(subscriptionId);
        } catch (RuntimeException e) {
            // The provider retries: unreachable, or the row kept changing concurrently.
            meterRegistry.counter(WEBHOOK_METRIC, "result", "failure").increment();
            throw e;
        }
        meterRegistry.counter(WEBHOOK_METRIC, "result", result).increment();
        if (UNKNOWN_CUSTOMER.equals(result)) {
            // Answered with 2xx anyway: the provider would only retry a subscription we can never assign.
            log.warn("Webhook for subscription {} of an unknown customer ignored", subscriptionId);
        }
    }

    /**
     * A webhook can get lost; then the provider may report a subscription running long after it ended. Those whose
     * paid period is over are asked for again each night (renewed, still retrying the payment, or ended).
     */
    @Override
    @Scheduled(cron = "${bm.cleanup.cron}")
    public void resyncOverdue() {
        List<String> overdue = subscriptionRepository.findRunningUnpaidSince(SubscriptionProvider.PADDLE, RUNNING,
                LocalDate.now(clock));
        for (String subscriptionId : overdue) {
            try {
                String result = sync(subscriptionId);
                meterRegistry.counter(RESYNC_METRIC, "result", result).increment();
                if (UNKNOWN_CUSTOMER.equals(result)) {
                    log.warn("Re-sync: subscription {} unknown to the provider, left as it is", subscriptionId);
                }
            } catch (RuntimeException e) {
                // Logged without personal data; access ends with the tolerance if the provider stays unreachable.
                meterRegistry.counter(RESYNC_METRIC, "result", "failure").increment();
                log.warn("Re-sync of subscription {} failed: {}", subscriptionId, e.getClass().getSimpleName());
            }
        }
        if (!overdue.isEmpty()) {
            log.info("Re-sync: checked {} subscriptions with an overdue paid period", overdue.size());
        }
    }

    /**
     * Revoked right away: the account and with it everything the subscription paid for is gone. After the first year
     * the prepaid rest is refunded pro rata; the state is read before, because the provider drops the paid period once
     * the subscription is revoked.
     */
    @Override
    public void revokeSubscriptionsOf(AppUser user) {
        subscriptionRepository.findAllByUser_Id(user.getId()).stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.ENDED)
                .forEach(subscription -> {
                    Optional<ProviderSubscription> before =
                            paymentProviderService.getSubscription(subscription.getExternalId());
                    if (paymentProviderService.revokeSubscription(subscription.getExternalId())) {
                        log.info("Subscription {} of user {} revoked before account deletion", subscription.getId(),
                                user.getId());
                        before.ifPresent(current -> earlyEndComponent.refundAfterDeletion(subscription, current));
                    }
                });
    }

    @Override
    public void removeSubscriptionsOf(AppUser user) {
        List<Subscription> subscriptions = subscriptionRepository.findAllByUser_Id(user.getId());
        // A refund promised for an early end must not vanish with the row: the administrators finish it by hand.
        subscriptions.forEach(earlyEndComponent::accountDeleted);
        // Removed here, not only by ON DELETE CASCADE: loaded entities must not point to the deleted user.
        subscriptionRepository.deleteAll(subscriptions);
    }

    /**
     * Set only if still empty, in the database: two checkouts at the same time both use the reference stored first,
     * so the payment of either can be assigned.
     */
    private String customerRefOf(AppUser user) {
        if (user.getBillingCustomerRef() != null) {
            return user.getBillingCustomerRef();
        }
        appUserRepository.setBillingCustomerRefIfMissing(user.getId(), UUID.randomUUID().toString());
        String ref = appUserRepository.findBillingCustomerRefById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " has no customer reference"));
        user.setBillingCustomerRef(ref);
        return ref;
    }

    /**
     * Fetches the state outside of a transaction, then stores it in a short one. Two webhooks of one subscription at
     * the same time: the one that saves second fails (version or unique key) and is stored again on the current row,
     * where the change it would report is known already – so the administrators get one email, not two.
     *
     * @return {@link #SYNCED} or {@link #UNKNOWN_CUSTOMER}
     */
    private String sync(String subscriptionId) {
        Optional<ProviderSubscription> found = paymentProviderService.getSubscription(subscriptionId);
        if (found.isEmpty()) {
            return UNKNOWN_CUSTOMER;
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionOperations.execute(status -> store(found.get()));
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                if (attempt >= MAX_STORE_ATTEMPTS) {
                    throw e;
                }
                log.info("Subscription {} changed concurrently, stored again (attempt {})", subscriptionId, attempt);
            }
        }
    }

    private String store(ProviderSubscription current) {
        Optional<AppUser> owner = Optional.ofNullable(current.customerRef())
                .flatMap(appUserRepository::findByBillingCustomerRef);
        if (owner.isEmpty()) {
            return UNKNOWN_CUSTOMER;
        }
        Subscription subscription = subscriptionRepository
                .findByProviderAndExternalId(SubscriptionProvider.PADDLE, current.id())
                .orElseGet(() -> Subscription.builder()
                        .user(owner.get())
                        .provider(SubscriptionProvider.PADDLE)
                        .externalId(current.id())
                        .build());
        SubscriptionStatus previous = subscription.getStatus();
        SubscriptionStatus status = statusOf(current);
        subscription.setStatus(status);
        subscription.setPaidUntil(paidUntil(current, status));
        subscription.setSyncedAt(LocalDateTime.now(clock));
        // Only the change counts: the provider sends several events per change and repeats them. Before saving, so
        // a planned early end is stored together with the status; emails go out only once it is committed.
        if (status == SubscriptionStatus.CANCELED && previous != SubscriptionStatus.CANCELED) {
            cancellationNoticeComponent.cancellationReceived(owner.get(), subscription, current);
        } else if (previous == SubscriptionStatus.CANCELED
                && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE)) {
            cancellationNoticeComponent.cancellationWithdrawn(subscription);
        }
        Subscription saved = subscriptionRepository.saveAndFlush(subscription);
        log.info("Subscription {} of user {} is now {}", saved.getId(), owner.get().getId(), status);
        return SYNCED;
    }

    /**
     * Paddle statuses: {@code trialing}/{@code active} run (cancelled ones, with a scheduled cancel, until the period
     * ends), {@code past_due} retries the payment, {@code canceled} is over (at the end of the period, or right away
     * after a revoke or an EU withdrawal); {@code paused} gives no access.
     */
    static SubscriptionStatus statusOf(ProviderSubscription subscription) {
        return switch (subscription.status()) {
            case "active", "trialing" -> subscription.cancelAtPeriodEnd()
                    ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            default -> SubscriptionStatus.ENDED;
        };
    }

    /** An ended subscription counts until the day it ended, even if the paid period would have been longer. */
    private LocalDate paidUntil(ProviderSubscription subscription, SubscriptionStatus status) {
        OffsetDateTime end = status == SubscriptionStatus.ENDED ? subscription.endedAt()
                : subscription.currentPeriodEnd();
        return end == null ? null : end.atZoneSameInstant(clock.getZone()).toLocalDate();
    }
}
