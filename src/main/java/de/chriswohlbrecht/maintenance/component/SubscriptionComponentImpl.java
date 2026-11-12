package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.PolarProperties;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription via Polar. The provider is the source of truth: a webhook is only a trigger to fetch the current
 * state of the subscription, so duplicates and events out of order do no harm.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class SubscriptionComponentImpl implements ISubscriptionComponent {

    static final String CHECKOUT_METRIC = "bm.subscription.checkout";
    static final String WEBHOOK_METRIC = "bm.subscription.webhook";
    private static final String SUBSCRIPTION_EVENT_PREFIX = "subscription.";
    private static final String ACCOUNT_PATH = "/konto";

    private final ICurrentUserComponent currentUserComponent;
    private final IAccessComponent accessComponent;
    private final IPaymentProviderService paymentProviderService;
    private final ICancellationNoticeComponent cancellationNoticeComponent;
    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PolarProperties polarProperties;
    private final AccountMailProperties accountMailProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressWarnings("java:S107") // user, access, provider, persistence, configuration and monitoring
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public SubscriptionComponentImpl(ICurrentUserComponent currentUserComponent, IAccessComponent accessComponent,
                                     IPaymentProviderService paymentProviderService,
                                     ICancellationNoticeComponent cancellationNoticeComponent,
                                     AppUserRepository appUserRepository,
                                     SubscriptionRepository subscriptionRepository, PolarProperties polarProperties,
                                     AccountMailProperties accountMailProperties, JsonMapper jsonMapper, Clock clock,
                                     MeterRegistry meterRegistry) {
        this.currentUserComponent = currentUserComponent;
        this.accessComponent = accessComponent;
        this.paymentProviderService = paymentProviderService;
        this.cancellationNoticeComponent = cancellationNoticeComponent;
        this.appUserRepository = appUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.polarProperties = polarProperties;
        this.accountMailProperties = accountMailProperties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        for (String result : List.of("started", "already_subscribed", "failure")) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", result);
        }
        for (String result : List.of("synced", "ignored", "unknown_customer", "invalid_signature", "failure")) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", result);
        }
    }

    /** The random reference is created on the first checkout and kept, so all subscriptions land on one customer. */
    @Override
    @Transactional
    public String startCheckout() {
        AppUser user = currentUserComponent.getCurrentUser();
        if (accessComponent.accessOf(user).status() == AccessStatus.ACTIVE) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", "already_subscribed").increment();
            throw new AlreadySubscribedException();
        }
        if (user.getBillingCustomerRef() == null) {
            user.setBillingCustomerRef(UUID.randomUUID().toString());
            appUserRepository.save(user);
        }
        try {
            String url = paymentProviderService.createCheckout(user.getBillingCustomerRef(), user.getEmail(),
                    accountUrl() + "?abo=danke", accountUrl());
            meterRegistry.counter(CHECKOUT_METRIC, "result", "started").increment();
            log.info("User {} started a checkout", user.getId());
            return url;
        } catch (PaymentProviderUnavailableException e) {
            meterRegistry.counter(CHECKOUT_METRIC, "result", "failure").increment();
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> openCustomerPortal() {
        AppUser user = currentUserComponent.getCurrentUser();
        if (user.getBillingCustomerRef() == null || subscriptionRepository.findAllByUser_Id(user.getId()).isEmpty()) {
            return Optional.empty();
        }
        return paymentProviderService.createCustomerPortal(user.getBillingCustomerRef(), accountUrl());
    }

    @Override
    @Transactional
    public void handleWebhook(String id, String timestamp, String signature, String body) {
        try {
            WebhookSignatureHelper.verify(polarProperties.webhookSecret(), id, timestamp, signature, body,
                    clock.instant());
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
        String type = event.path("type").asString("");
        String subscriptionId = event.path("data").path("id").asString("");
        if (!type.startsWith(SUBSCRIPTION_EVENT_PREFIX)) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", "ignored").increment();
            return;
        }
        if (subscriptionId.isBlank()) {
            throw new InvalidWebhookException("Subscription event without data.id");
        }
        try {
            sync(subscriptionId);
        } catch (PaymentProviderUnavailableException e) {
            meterRegistry.counter(WEBHOOK_METRIC, "result", "failure").increment();
            throw e;
        }
    }

    /** Revoked right away: the account and with it everything the subscription paid for is gone. */
    @Override
    public void endSubscriptionsOf(AppUser user) {
        List<Subscription> subscriptions = subscriptionRepository.findAllByUser_Id(user.getId());
        subscriptions.stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.ENDED)
                .forEach(subscription -> {
                    paymentProviderService.revokeSubscription(subscription.getExternalId());
                    log.info("Subscription {} of user {} revoked before account deletion", subscription.getId(),
                            user.getId());
                });
        // Removed here, not only by ON DELETE CASCADE: loaded entities must not point to the deleted user.
        subscriptionRepository.deleteAll(subscriptions);
    }

    private void sync(String subscriptionId) {
        Optional<ProviderSubscription> found = paymentProviderService.getSubscription(subscriptionId);
        Optional<AppUser> owner = found.map(ProviderSubscription::customerRef)
                .flatMap(appUserRepository::findByBillingCustomerRef);
        if (found.isEmpty() || owner.isEmpty()) {
            // Answered with 2xx anyway: the provider would only retry a subscription we can never assign.
            meterRegistry.counter(WEBHOOK_METRIC, "result", "unknown_customer").increment();
            log.warn("Webhook for subscription {} of an unknown customer ignored", subscriptionId);
            return;
        }
        ProviderSubscription current = found.get();
        Subscription subscription = subscriptionRepository
                .findByProviderAndExternalId(SubscriptionProvider.POLAR, current.id())
                .orElseGet(() -> Subscription.builder()
                        .user(owner.get())
                        .provider(SubscriptionProvider.POLAR)
                        .externalId(current.id())
                        .build());
        SubscriptionStatus previous = subscription.getStatus();
        SubscriptionStatus status = statusOf(current);
        subscription.setStatus(status);
        subscription.setPaidUntil(paidUntil(current, status));
        // Only the change counts: the provider sends several events per change and repeats them. Before saving, so
        // a planned early end is stored together with the status.
        if (status == SubscriptionStatus.CANCELED && previous != SubscriptionStatus.CANCELED) {
            cancellationNoticeComponent.cancellationReceived(owner.get(), subscription, current);
        } else if (previous == SubscriptionStatus.CANCELED
                && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE)) {
            cancellationNoticeComponent.cancellationWithdrawn(subscription);
        }
        subscriptionRepository.save(subscription);
        meterRegistry.counter(WEBHOOK_METRIC, "result", "synced").increment();
        log.info("Subscription {} of user {} is now {}", subscription.getId(), owner.get().getId(), status);
    }

    /**
     * Polar statuses: {@code trialing}/{@code active} run (cancelled ones until the period ends), {@code past_due}
     * retries the payment, {@code canceled} with {@code ended_at} is over; {@code unpaid}, {@code paused},
     * {@code incomplete} and {@code incomplete_expired} give no access.
     */
    static SubscriptionStatus statusOf(ProviderSubscription subscription) {
        return switch (subscription.status()) {
            case "active", "trialing" -> subscription.cancelAtPeriodEnd()
                    ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> subscription.endedAt() == null ? SubscriptionStatus.CANCELED : SubscriptionStatus.ENDED;
            default -> SubscriptionStatus.ENDED;
        };
    }

    /** An ended subscription counts until the day it ended, even if the paid period would have been longer. */
    private LocalDate paidUntil(ProviderSubscription subscription, SubscriptionStatus status) {
        OffsetDateTime end = status == SubscriptionStatus.ENDED ? subscription.endedAt()
                : subscription.currentPeriodEnd();
        return end == null ? null : end.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    private String accountUrl() {
        return accountMailProperties.linkBaseUrl() + ACCOUNT_PATH;
    }
}
