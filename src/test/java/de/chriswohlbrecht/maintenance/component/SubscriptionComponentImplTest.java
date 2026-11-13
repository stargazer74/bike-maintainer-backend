package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
import de.chriswohlbrecht.maintenance.exception.AlreadySubscribedException;
import de.chriswohlbrecht.maintenance.exception.InvalidWebhookException;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import de.chriswohlbrecht.maintenance.exception.WebhookSignatureException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.service.IPaymentProviderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionComponentImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final String SECRET = "pdl_ntfset_secret";
    private static final OffsetDateTime PERIOD_END = OffsetDateTime.parse("2027-11-12T10:00:00Z");

    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private IAccessComponent accessComponent;
    @Mock
    private IPaymentProviderService paymentProviderService;
    @Mock
    private ICancellationNoticeComponent cancellationNoticeComponent;
    @Mock
    private IEarlyEndComponent earlyEndComponent;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private SubscriptionComponentImpl component;
    private AppUser user;

    @BeforeEach
    void setUp() {
        component = new SubscriptionComponentImpl(currentUserComponent, accessComponent, paymentProviderService,
                cancellationNoticeComponent, earlyEndComponent, appUserRepository, subscriptionRepository,
                new PaddleProperties("https://api", "key", SECRET, "pri_1", "test_token"), new JsonMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry, TransactionOperations.withoutTransaction());
        user = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).build();
        lenient().when(subscriptionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void firstCheckoutCreatesARandomReferenceAndReturnsTheCheckoutPage() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.TRIAL, null, false));
        ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
        when(appUserRepository.setBillingCustomerRefIfMissing(eq(7L), created.capture())).thenReturn(1);
        when(appUserRepository.findBillingCustomerRefById(7L)).thenAnswer(call -> Optional.of(created.getValue()));
        when(paymentProviderService.createCheckout(anyString(), eq("rider@example.org")))
                .thenReturn("https://app.example.org/abo/bezahlen?_ptxn=txn_1");

        assertThat(component.startCheckout()).isEqualTo("https://app.example.org/abo/bezahlen?_ptxn=txn_1");

        assertThat(created.getValue()).hasSize(36).isNotEqualTo(String.valueOf(user.getId()));
        assertThat(user.getBillingCustomerRef()).isEqualTo(created.getValue());
        verify(paymentProviderService).createCheckout(eq(created.getValue()), any());
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.CHECKOUT_METRIC, "result", "started").count())
                .isEqualTo(1.0);
    }

    @Test
    void laterCheckoutsKeepTheReference() {
        user.setBillingCustomerRef("ref-1");
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.EXPIRED, null, true));
        when(paymentProviderService.createCheckout(eq("ref-1"), any())).thenReturn("https://c");

        component.startCheckout();

        verify(appUserRepository, never()).setBillingCustomerRefIfMissing(any(), any());
    }

    /** Two checkouts at the same time: the one that comes second uses the reference stored by the first. */
    @Test
    void concurrentFirstCheckoutUsesTheReferenceStoredFirst() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.TRIAL, null, false));
        when(appUserRepository.setBillingCustomerRefIfMissing(eq(7L), anyString())).thenReturn(0);
        when(appUserRepository.findBillingCustomerRefById(7L)).thenReturn(Optional.of("ref-first"));
        when(paymentProviderService.createCheckout(eq("ref-first"), any())).thenReturn("https://c");

        assertThat(component.startCheckout()).isEqualTo("https://c");
        assertThat(user.getBillingCustomerRef()).isEqualTo("ref-first");
    }

    @Test
    void checkoutWithAnActiveSubscriptionIsRejected() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.ACTIVE, null, true));

        assertThatThrownBy(component::startCheckout).isInstanceOf(AlreadySubscribedException.class);
        verifyNoInteractions(paymentProviderService);
    }

    @Test
    void unreachableProviderIsCountedAsFailedCheckout() {
        user.setBillingCustomerRef("ref-1");
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.TRIAL, null, false));
        when(paymentProviderService.createCheckout(any(), any()))
                .thenThrow(new PaymentProviderUnavailableException("down"));

        assertThatThrownBy(component::startCheckout).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.CHECKOUT_METRIC, "result", "failure").count())
                .isEqualTo(1.0);
    }

    /** Opened through the newest subscription of the account, whose customer the portal belongs to. */
    @Test
    void customerPortalOnlyForAccountsWithASubscription() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of());
        assertThat(component.openCustomerPortal()).isEmpty();
        verifyNoInteractions(paymentProviderService);

        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of(
                Subscription.builder().id(1L).externalId("sub_old").build(),
                Subscription.builder().id(2L).externalId("sub_new").build()));
        when(paymentProviderService.createCustomerPortal("sub_new"))
                .thenReturn(Optional.of("https://customer-portal.paddle.com/cpl_1"));
        assertThat(component.openCustomerPortal()).contains("https://customer-portal.paddle.com/cpl_1");
    }

    @Test
    void webhookWithAWrongSignatureChangesNothing() {
        assertThatThrownBy(() -> component.handleWebhook("ts=" + NOW.getEpochSecond() + ";h1=" + "ab".repeat(32),
                event("sub_1")))
                .isInstanceOf(WebhookSignatureException.class);

        verifyNoInteractions(paymentProviderService, subscriptionRepository);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "invalid_signature")
                .count()).isEqualTo(1.0);
    }

    @Test
    void webhookCreatesTheSubscriptionFromTheProvidersCurrentState() {
        user.setBillingCustomerRef("ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.empty());

        receive(event("sub_1"));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExternalId()).isEqualTo("sub_1");
        assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getValue().getPaidUntil()).isEqualTo(LocalDate.of(2027, 11, 12));
        assertThat(saved.getValue().getSyncedAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 12, 0));
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "synced").count())
                .isEqualTo(1.0);
    }

    /** Two webhooks at the same time: the one that lost is stored again on the row the other one saved. */
    @Test
    void webhookThatLosesAgainstAConcurrentOneIsStoredAgain() {
        user.setBillingCustomerRef("ref-1");
        Subscription stored = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.CANCELED).build();
        ProviderSubscription cancelled = new ProviderSubscription("sub_1", "active", PERIOD_END, null, true, "ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(cancelled));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.empty(), Optional.of(stored));
        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_subscription_provider_external"))
                .thenAnswer(call -> call.getArgument(0));

        receive(event("sub_1"));

        verify(subscriptionRepository, times(2)).saveAndFlush(any());
        // The first attempt was rolled back; on the current row the cancellation is known already.
        verify(cancellationNoticeComponent, times(1)).cancellationReceived(any(), any(), any());
        verify(cancellationNoticeComponent, never()).cancellationReceived(user, stored, cancelled);
    }

    @Test
    void webhookThatKeepsLosingIsReportedSoTheProviderRetries() {
        user.setBillingCustomerRef("ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Subscription.class, 1L));

        assertThatThrownBy(() -> receive(event("sub_1"))).isInstanceOf(ConcurrencyFailureException.class);

        verify(subscriptionRepository, times(SubscriptionComponentImpl.MAX_STORE_ATTEMPTS)).saveAndFlush(any());
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "failure").count())
                .isEqualTo(1.0);
    }

    /** A lost webhook: subscriptions reported running after their paid period are asked for again each night. */
    @Test
    void resyncAsksTheProviderAgainForOverdueRunningSubscriptions() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.ACTIVE).paidUntil(LocalDate.of(2026, 11, 1)).build();
        when(subscriptionRepository.findRunningUnpaidSince(SubscriptionProvider.PADDLE,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE), LocalDate.of(2026, 11, 12)))
                .thenReturn(List.of("sub_1", "sub_gone", "sub_down"));
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(new ProviderSubscription(
                "sub_1", "canceled", null, OffsetDateTime.parse("2026-11-01T08:00:00Z"), false, "ref-1")));
        when(paymentProviderService.getSubscription("sub_gone")).thenReturn(Optional.empty());
        when(paymentProviderService.getSubscription("sub_down")).thenThrow(new PaymentProviderUnavailableException("x"));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.of(existing));

        component.resyncOverdue();

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.RESYNC_METRIC, "result", "synced").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.RESYNC_METRIC, "result", "unknown_customer")
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.RESYNC_METRIC, "result", "failure").count())
                .isEqualTo(1.0);
    }

    @Test
    void resyncWithNothingOverdueDoesNothing() {
        when(subscriptionRepository.findRunningUnpaidSince(any(), any(), any())).thenReturn(List.of());

        component.resyncOverdue();

        verifyNoInteractions(paymentProviderService);
    }

    /** Revoked before the period ended (e.g. refund): access ends with the day it ended. */
    @Test
    void webhookUpdatesAnEndedSubscriptionToTheDayItEnded() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.ACTIVE).paidUntil(LocalDate.of(2027, 11, 12)).build();
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(new ProviderSubscription(
                "sub_1", "canceled", null, OffsetDateTime.parse("2026-11-12T08:00:00Z"), false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(existing.getPaidUntil()).isEqualTo(LocalDate.of(2026, 11, 12));
        verify(subscriptionRepository).saveAndFlush(existing);
    }

    /** Only the change to "cancelled" counts, not every repeated event of it. */
    @Test
    void aNewCancellationIsPassedOnOnce() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.ACTIVE).build();
        ProviderSubscription cancelled = new ProviderSubscription("sub_1", "active", PERIOD_END, null, true, "ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(cancelled));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));
        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(cancellationNoticeComponent, times(1)).cancellationReceived(user, existing, cancelled);
    }

    @Test
    void otherChangesAreNoCancellation() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.CANCELED).build();
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(cancellationNoticeComponent).cancellationWithdrawn(existing);
        verify(cancellationNoticeComponent, never()).cancellationReceived(any(), any(), any());
    }

    @Test
    void anEndedSubscriptionIsNoWithdrawal() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.PADDLE)
                .externalId("sub_1").status(SubscriptionStatus.CANCELED).build();
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "canceled", null, PERIOD_END, false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.PADDLE, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        verifyNoInteractions(cancellationNoticeComponent);
    }

    @Test
    void webhookOfAnUnknownCustomerIsIgnored() {
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-unknown")));
        when(appUserRepository.findByBillingCustomerRef("ref-unknown")).thenReturn(Optional.empty());

        receive(event("sub_1"));

        verify(subscriptionRepository, never()).saveAndFlush(any());
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "unknown_customer")
                .count()).isEqualTo(1.0);
    }

    @Test
    void webhookOfAnUnknownSubscriptionIsIgnored() {
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.empty());

        receive(event("sub_1"));

        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void otherEventsAreIgnored() {
        receive("{\"event_type\":\"transaction.completed\",\"data\":{\"id\":\"txn_1\"}}");

        verifyNoInteractions(paymentProviderService);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "ignored").count())
                .isEqualTo(1.0);
    }

    @Test
    void unreadableOrIncompleteEventsAreRejected() {
        assertThatThrownBy(() -> receive("not json")).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> receive("{\"event_type\":\"subscription.activated\",\"data\":{}}"))
                .isInstanceOf(InvalidWebhookException.class);
    }

    /** Answered with 503, so the provider sends the event again later. */
    @Test
    void unreachableProviderDuringAWebhookIsReported() {
        when(paymentProviderService.getSubscription("sub_1")).thenThrow(new PaymentProviderUnavailableException("x"));

        assertThatThrownBy(() -> receive(event("sub_1"))).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "failure").count())
                .isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({
            "active, false, false, ACTIVE",
            "trialing, false, false, ACTIVE",
            "active, true, false, CANCELED",
            "past_due, false, false, PAST_DUE",
            "trialing, true, false, CANCELED",
            "canceled, false, true, ENDED",
            "paused, false, false, ENDED"
    })
    void providerStatusIsMapped(String status, boolean cancelAtPeriodEnd, boolean ended, SubscriptionStatus expected) {
        ProviderSubscription subscription = new ProviderSubscription("sub_1", status, PERIOD_END,
                ended ? PERIOD_END : null, cancelAtPeriodEnd, "ref-1");

        assertThat(SubscriptionComponentImpl.statusOf(subscription)).isEqualTo(expected);
    }

    /**
     * The state is read before the revoke (the provider drops the paid period afterwards) and handed on for the refund
     * after the first year; a subscription the provider had ended already gets no refund.
     */
    @Test
    void revokeSubscriptionsOfRevokesAllThatHaveNotEndedAndHandsThemOnForTheRefund() {
        Subscription active = Subscription.builder().id(1L).externalId("sub_1").status(SubscriptionStatus.ACTIVE).build();
        Subscription canceled = Subscription.builder().id(3L).externalId("sub_3").status(SubscriptionStatus.CANCELED)
                .build();
        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of(active,
                Subscription.builder().id(2L).externalId("sub_2").status(SubscriptionStatus.ENDED).build(), canceled));
        ProviderSubscription before = new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(before));
        when(paymentProviderService.getSubscription("sub_3")).thenReturn(Optional.empty());
        when(paymentProviderService.revokeSubscription("sub_1")).thenReturn(true);
        when(paymentProviderService.revokeSubscription("sub_3")).thenReturn(false);

        component.revokeSubscriptionsOf(user);

        InOrder order = inOrder(paymentProviderService, earlyEndComponent);
        order.verify(paymentProviderService).getSubscription("sub_1");
        order.verify(paymentProviderService).revokeSubscription("sub_1");
        order.verify(earlyEndComponent).refundAfterDeletion(active, before);
        verify(paymentProviderService).revokeSubscription("sub_3");
        verify(paymentProviderService, never()).revokeSubscription("sub_2");
        verify(earlyEndComponent, never()).refundAfterDeletion(eq(canceled), any());
        verify(subscriptionRepository, never()).deleteAll(any());
    }

    /** An early end under way goes to the administrators before its row is removed with the account. */
    @Test
    void removeSubscriptionsOfHandsOpenEarlyEndsOverThenDeletes() {
        Subscription first = Subscription.builder().id(1L).externalId("sub_1").build();
        Subscription second = Subscription.builder().id(2L).externalId("sub_2").build();
        List<Subscription> subscriptions = List.of(first, second);
        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(subscriptions);

        component.removeSubscriptionsOf(user);

        InOrder order = inOrder(earlyEndComponent, subscriptionRepository);
        order.verify(earlyEndComponent).accountDeleted(first);
        order.verify(earlyEndComponent).accountDeleted(second);
        order.verify(subscriptionRepository).deleteAll(subscriptions);
        verifyNoInteractions(paymentProviderService);
    }

    private void receive(String body) {
        component.handleWebhook(WebhookSignatureHelper.signatureHeader(SECRET, NOW.getEpochSecond(), body), body);
    }

    private static String event(String subscriptionId) {
        return "{\"event_type\":\"subscription.updated\",\"data\":{\"id\":\"" + subscriptionId + "\"}}";
    }
}
