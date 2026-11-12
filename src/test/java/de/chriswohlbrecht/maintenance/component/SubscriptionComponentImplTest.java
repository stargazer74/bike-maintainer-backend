package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.configuration.PolarProperties;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionComponentImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("secret".getBytes(StandardCharsets.UTF_8));
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
    private AppUserRepository appUserRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private SubscriptionComponentImpl component;
    private AppUser user;

    @BeforeEach
    void setUp() {
        component = new SubscriptionComponentImpl(currentUserComponent, accessComponent, paymentProviderService,
                cancellationNoticeComponent, appUserRepository, subscriptionRepository,
                new PolarProperties("https://api", "token", SECRET, "product-1"),
                new AccountMailProperties("noreply@example.org", 100, "https://app.example.org/"), new JsonMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
        user = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).build();
    }

    @Test
    void firstCheckoutCreatesARandomReferenceAndReturnsTheCheckoutPage() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.TRIAL, null, false));
        when(paymentProviderService.createCheckout(anyString(), eq("rider@example.org"),
                eq("https://app.example.org/konto?abo=danke"), eq("https://app.example.org/konto")))
                .thenReturn("https://polar.sh/checkout/1");

        assertThat(component.startCheckout()).isEqualTo("https://polar.sh/checkout/1");

        assertThat(user.getBillingCustomerRef()).hasSize(36).isNotEqualTo(String.valueOf(user.getId()));
        verify(appUserRepository).save(user);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.CHECKOUT_METRIC, "result", "started").count())
                .isEqualTo(1.0);
    }

    @Test
    void laterCheckoutsKeepTheReference() {
        user.setBillingCustomerRef("ref-1");
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.EXPIRED, null, true));
        when(paymentProviderService.createCheckout(eq("ref-1"), any(), any(), any())).thenReturn("https://c");

        component.startCheckout();

        verify(appUserRepository, never()).save(any());
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
        when(paymentProviderService.createCheckout(any(), any(), any(), any()))
                .thenThrow(new PaymentProviderUnavailableException("down"));

        assertThatThrownBy(component::startCheckout).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.CHECKOUT_METRIC, "result", "failure").count())
                .isEqualTo(1.0);
    }

    @Test
    void customerPortalOnlyForAccountsWithASubscription() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        assertThat(component.openCustomerPortal()).isEmpty();

        user.setBillingCustomerRef("ref-1");
        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of());
        assertThat(component.openCustomerPortal()).isEmpty();
        verifyNoInteractions(paymentProviderService);

        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of(new Subscription()));
        when(paymentProviderService.createCustomerPortal("ref-1", "https://app.example.org/konto"))
                .thenReturn(Optional.of("https://polar.sh/portal"));
        assertThat(component.openCustomerPortal()).contains("https://polar.sh/portal");
    }

    @Test
    void webhookWithAWrongSignatureChangesNothing() {
        assertThatThrownBy(() -> component.handleWebhook("msg_1", timestamp(), "v1,d3Jvbmc=", event("sub_1")))
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
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.POLAR, "sub_1"))
                .thenReturn(Optional.empty());

        receive(event("sub_1"));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExternalId()).isEqualTo("sub_1");
        assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getValue().getPaidUntil()).isEqualTo(LocalDate.of(2027, 11, 12));
    }

    /** Revoked before the period ended (e.g. refund): access ends with the day it ended. */
    @Test
    void webhookUpdatesAnEndedSubscriptionToTheDayItEnded() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.POLAR)
                .externalId("sub_1").status(SubscriptionStatus.ACTIVE).paidUntil(LocalDate.of(2027, 11, 12)).build();
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(new ProviderSubscription(
                "sub_1", "canceled", PERIOD_END, OffsetDateTime.parse("2026-11-12T08:00:00Z"), false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.POLAR, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(existing.getPaidUntil()).isEqualTo(LocalDate.of(2026, 11, 12));
        verify(subscriptionRepository).save(existing);
    }

    /** Only the change to "cancelled" counts, not every repeated event of it. */
    @Test
    void aNewCancellationIsPassedOnOnce() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.POLAR)
                .externalId("sub_1").status(SubscriptionStatus.ACTIVE).build();
        ProviderSubscription cancelled = new ProviderSubscription("sub_1", "active", PERIOD_END, null, true, "ref-1");
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(cancelled));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.POLAR, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));
        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(cancellationNoticeComponent, times(1)).cancellationReceived(user, cancelled);
    }

    @Test
    void otherChangesAreNoCancellation() {
        Subscription existing = Subscription.builder().user(user).provider(SubscriptionProvider.POLAR)
                .externalId("sub_1").status(SubscriptionStatus.CANCELED).build();
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-1")));
        when(appUserRepository.findByBillingCustomerRef("ref-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByProviderAndExternalId(SubscriptionProvider.POLAR, "sub_1"))
                .thenReturn(Optional.of(existing));

        receive(event("sub_1"));

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verifyNoInteractions(cancellationNoticeComponent);
    }

    @Test
    void webhookOfAnUnknownCustomerIsIgnored() {
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.of(
                new ProviderSubscription("sub_1", "active", PERIOD_END, null, false, "ref-unknown")));
        when(appUserRepository.findByBillingCustomerRef("ref-unknown")).thenReturn(Optional.empty());

        receive(event("sub_1"));

        verify(subscriptionRepository, never()).save(any());
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "unknown_customer")
                .count()).isEqualTo(1.0);
    }

    @Test
    void webhookOfAnUnknownSubscriptionIsIgnored() {
        when(paymentProviderService.getSubscription("sub_1")).thenReturn(Optional.empty());

        receive(event("sub_1"));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void otherEventsAreIgnored() {
        receive("{\"type\":\"order.created\",\"data\":{\"id\":\"ord_1\"}}");

        verifyNoInteractions(paymentProviderService);
        assertThat(meterRegistry.counter(SubscriptionComponentImpl.WEBHOOK_METRIC, "result", "ignored").count())
                .isEqualTo(1.0);
    }

    @Test
    void unreadableOrIncompleteEventsAreRejected() {
        assertThatThrownBy(() -> receive("not json")).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> receive("{\"type\":\"subscription.active\",\"data\":{}}"))
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
            "canceled, false, false, CANCELED",
            "canceled, false, true, ENDED",
            "unpaid, false, false, ENDED",
            "paused, false, false, ENDED",
            "incomplete, false, false, ENDED",
            "incomplete_expired, false, false, ENDED"
    })
    void providerStatusIsMapped(String status, boolean cancelAtPeriodEnd, boolean ended, SubscriptionStatus expected) {
        ProviderSubscription subscription = new ProviderSubscription("sub_1", status, PERIOD_END,
                ended ? PERIOD_END : null, cancelAtPeriodEnd, "ref-1");

        assertThat(SubscriptionComponentImpl.statusOf(subscription)).isEqualTo(expected);
    }

    @Test
    void endSubscriptionsOfRevokesAllThatHaveNotEnded() {
        when(subscriptionRepository.findAllByUser_Id(7L)).thenReturn(List.of(
                Subscription.builder().id(1L).externalId("sub_1").status(SubscriptionStatus.ACTIVE).build(),
                Subscription.builder().id(2L).externalId("sub_2").status(SubscriptionStatus.ENDED).build(),
                Subscription.builder().id(3L).externalId("sub_3").status(SubscriptionStatus.CANCELED).build()));

        component.endSubscriptionsOf(user);

        verify(paymentProviderService).revokeSubscription("sub_1");
        verify(paymentProviderService).revokeSubscription("sub_3");
        verify(paymentProviderService, never()).revokeSubscription("sub_2");
        verify(subscriptionRepository).deleteAll(any());
    }

    private void receive(String body) {
        String timestamp = timestamp();
        component.handleWebhook("msg_1", timestamp, WebhookSignatureHelper.signature(SECRET, "msg_1", timestamp, body),
                body);
    }

    private static String event(String subscriptionId) {
        return "{\"type\":\"subscription.updated\",\"data\":{\"id\":\"" + subscriptionId + "\"}}";
    }

    private static String timestamp() {
        return String.valueOf(NOW.getEpochSecond());
    }
}
