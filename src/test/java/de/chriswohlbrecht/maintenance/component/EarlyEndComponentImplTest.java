package de.chriswohlbrecht.maintenance.component;

import java.time.OffsetDateTime;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;

import de.chriswohlbrecht.maintenance.component.model.CancellationMail;
import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.configuration.EarlyEndProperties;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.service.IPaymentProviderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EarlyEndComponentImplTest {

    /** The day after the end of the notice (2027-02-05). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-02-06T02:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate TODAY = LocalDate.of(2027, 2, 6);
    /** 280 of 365 days of 2400 cents: 1841. */
    private static final long REFUND = 1841L;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private IPaymentProviderService paymentProviderService;
    @Mock
    private IAccountMailComponent accountMailComponent;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AppUser owner = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).build();
    private final AppUser admin = AppUser.builder().id(1L).email("admin@example.org").role(UserRole.ADMIN)
            .active(true).build();
    /** States as saved, in order: the job must save each step before the next call to the provider. */
    private final List<EarlyEndState> saved = new ArrayList<>();
    private EarlyEndComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new EarlyEndComponentImpl(subscriptionRepository, appUserRepository, paymentProviderService,
                accountMailComponent, new EarlyEndProperties(true, 3), CLOCK, meterRegistry);
        when(appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            saved.add(subscription.getEarlyEndState());
            return subscription;
        });
    }

    @Test
    void countersAreRegisteredWithZero() {
        for (String result : List.of("scheduled", "withdrawn", "revoked", "refunded", "manual", "retry")) {
            assertThat(meterRegistry.find(EarlyEndComponentImpl.METRIC).tag("result", result).counter()).isNotNull();
        }
    }

    @Test
    void nothingDueDoesNothing() {
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of());

        component.carryOutDueEarlyEnds();

        verifyNoInteractions(paymentProviderService, accountMailComponent);
    }

    @Test
    void dueEarlyEndIsRevokedAndRefundedProRata() {
        Subscription subscription = due(EarlyEndState.SCHEDULED);
        when(paymentProviderService.revokeSubscription("sub_1")).thenReturn(true);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 0, 2400, "eur")));

        component.carryOutDueEarlyEnds();

        InOrder order = inOrder(paymentProviderService, subscriptionRepository);
        order.verify(paymentProviderService).revokeSubscription("sub_1");
        order.verify(subscriptionRepository).save(subscription);
        order.verify(paymentProviderService).findLastPeriodOrder("sub_1");
        order.verify(subscriptionRepository).save(subscription);
        order.verify(paymentProviderService).refund("ord_1", REFUND, "sub_1");
        assertThat(saved).containsExactly(EarlyEndState.REVOKED, EarlyEndState.REFUNDING, EarlyEndState.REFUNDED);
        assertThat(subscription.getEarlyEndOrderId()).isEqualTo("ord_1");
        assertThat(subscription.getEarlyEndRefundedBefore()).isZero();
        verify(accountMailComponent).sendCancellationNotice(admin, notice(REFUND, "eur"), CancellationMail.DONE,
                null);
        assertThat(count("revoked")).isEqualTo(1.0);
        assertThat(count("refunded")).isEqualTo(1.0);
    }

    /** Never more than Paddle can still refund, e.g. after a partial refund by hand. */
    @Test
    void refundIsCappedAtTheRefundableAmount() {
        due(EarlyEndState.REVOKED);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 1000, 1400, "eur")));

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService, never()).revokeSubscription(anyString());
        verify(paymentProviderService).refund("ord_1", 1400, "sub_1");
        verify(accountMailComponent).sendCancellationNotice(admin, notice(1400L, "eur"), CancellationMail.DONE,
                null);
    }

    @Test
    void nothingLeftToRefundFinishesWithoutRefund() {
        Subscription subscription = due(EarlyEndState.REVOKED);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 2400, 0, "eur")));

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService, never()).refund(anyString(), anyLong(), anyString());
        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.REFUNDED);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(0L, "eur"), CancellationMail.DONE, null);
    }

    /** The run broke off after the refund was requested, and the refund went through: not refunded again. */
    @Test
    void retryAfterARefundThatWentThroughDoesNotRefundTwice() {
        Subscription subscription = due(EarlyEndState.REFUNDING);
        subscription.setEarlyEndOrderId("ord_1");
        subscription.setEarlyEndRefundedBefore(100L);
        when(paymentProviderService.getOrder("ord_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 100 + REFUND, 2300 - REFUND, "eur")));

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService, never()).refund(anyString(), anyLong(), anyString());
        assertThat(saved).containsExactly(EarlyEndState.REFUNDED);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(REFUND, "eur"), CancellationMail.DONE,
                null);
    }

    /** The run broke off before the refund reached Paddle: refunded now. */
    @Test
    void retryAfterARefundThatDidNotGoThroughRefunds() {
        Subscription subscription = due(EarlyEndState.REFUNDING);
        subscription.setEarlyEndOrderId("ord_1");
        subscription.setEarlyEndRefundedBefore(0L);
        when(paymentProviderService.getOrder("ord_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 0, 2400, "eur")));

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService).refund("ord_1", REFUND, "sub_1");
        assertThat(saved).containsExactly(EarlyEndState.REFUNDED);
    }

    /** Paddle fails on the refund: the order is already stored, the next run checks it before refunding. */
    @Test
    void failedRefundIsRetriedFromTheStoredOrder() {
        Subscription subscription = due(EarlyEndState.REVOKED);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 0, 2400, "eur")));
        doThrow(new PaymentProviderUnavailableException("down")).when(paymentProviderService)
                .refund("ord_1", REFUND, "sub_1");

        component.carryOutDueEarlyEnds();

        assertThat(saved).containsExactly(EarlyEndState.REFUNDING, EarlyEndState.REFUNDING);
        assertThat(subscription.getEarlyEndOrderId()).isEqualTo("ord_1");
        assertThat(subscription.getEarlyEndAttempts()).isEqualTo(1);
        assertThat(count("retry")).isEqualTo(1.0);
        verifyNoInteractions(accountMailComponent);
    }

    @Test
    void alreadyEndedSubscriptionIsHandedOver() {
        Subscription subscription = due(EarlyEndState.SCHEDULED);
        when(paymentProviderService.revokeSubscription("sub_1")).thenReturn(false);

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService, never()).findLastPeriodOrder(anyString());
        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.MANUAL);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.alreadyEnded");
        assertThat(count("manual")).isEqualTo(1.0);
    }

    @Test
    void missingOrderIsHandedOver() {
        Subscription subscription = due(EarlyEndState.REVOKED);
        when(paymentProviderService.findLastPeriodOrder("sub_1")).thenReturn(Optional.empty());

        component.carryOutDueEarlyEnds();

        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.MANUAL);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.noOrder");
    }

    @Test
    void unknownStoredOrderIsHandedOver() {
        Subscription subscription = due(EarlyEndState.REFUNDING);
        subscription.setEarlyEndOrderId("ord_1");
        when(paymentProviderService.getOrder("ord_1")).thenReturn(Optional.empty());

        component.carryOutDueEarlyEnds();

        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.MANUAL);
    }

    /** Paddle down three nights in a row: handed over with the step it got stuck at. */
    @Test
    void afterTheLastAttemptTheEarlyEndIsHandedOver() {
        Subscription subscription = due(EarlyEndState.SCHEDULED);
        subscription.setEarlyEndAttempts(2);
        when(paymentProviderService.revokeSubscription("sub_1"))
                .thenThrow(new PaymentProviderUnavailableException("down"));

        component.carryOutDueEarlyEnds();

        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.MANUAL);
        assertThat(subscription.getEarlyEndAttempts()).isEqualTo(3);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.unreachableBeforeEnd");
    }

    @Test
    void handOverReasonFollowsTheStep() {
        Subscription revoked = due(EarlyEndState.REVOKED);
        revoked.setEarlyEndAttempts(2);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenThrow(new PaymentProviderUnavailableException("down"));

        component.carryOutDueEarlyEnds();

        verify(accountMailComponent).sendCancellationNotice(admin, notice(null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.unreachableBeforeRefund");
    }

    @Test
    void handOverDuringTheRefundAsksToCheckFirst() {
        Subscription refunding = due(EarlyEndState.REFUNDING);
        refunding.setEarlyEndOrderId("ord_1");
        refunding.setEarlyEndAttempts(2);
        when(paymentProviderService.getOrder("ord_1")).thenThrow(new PaymentProviderUnavailableException("down"));

        component.carryOutDueEarlyEnds();

        verify(accountMailComponent).sendCancellationNotice(admin, notice(null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.unreachableDuringRefund");
    }

    /** One subscription failing does not stop the next; a failure that cannot even be saved is only logged. */
    @Test
    void failuresAreIsolated() {
        Subscription first = subscription(EarlyEndState.SCHEDULED, "sub_1");
        Subscription second = subscription(EarlyEndState.SCHEDULED, "sub_2");
        second.setId(4L);
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of(first, second));
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(first));
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(second));
        when(paymentProviderService.revokeSubscription("sub_1")).thenThrow(new IllegalStateException("bug"));
        when(subscriptionRepository.save(first)).thenThrow(new IllegalStateException("db"));
        when(paymentProviderService.revokeSubscription("sub_2")).thenReturn(true);
        when(paymentProviderService.findLastPeriodOrder("sub_2"))
                .thenReturn(Optional.of(new ProviderOrder("ord_2", 2400, 0, 2400, "eur")));

        component.carryOutDueEarlyEnds();

        assertThat(first.getEarlyEndState()).isEqualTo(EarlyEndState.SCHEDULED);
        assertThat(second.getEarlyEndState()).isEqualTo(EarlyEndState.REFUNDED);
    }

    /**
     * Revoking makes Paddle send a webhook, which saves "ended" and the day it ended. The job saves its steps on that
     * current row, not on the copy it read before, so access really ends (backend review, finding 1).
     */
    @Test
    void aWebhookSavedWhileRevokingIsNotOverwritten() {
        Subscription read = subscription(EarlyEndState.SCHEDULED, "sub_1");
        Subscription afterWebhook = subscription(EarlyEndState.SCHEDULED, "sub_1");
        afterWebhook.setStatus(SubscriptionStatus.ENDED);
        afterWebhook.setPaidUntil(TODAY);
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of(read));
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(read), Optional.of(afterWebhook));
        when(paymentProviderService.revokeSubscription("sub_1")).thenReturn(true);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 0, 2400, "eur")));

        component.carryOutDueEarlyEnds();

        verify(subscriptionRepository, never()).save(read);
        assertThat(afterWebhook.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(afterWebhook.getPaidUntil()).isEqualTo(TODAY);
        assertThat(afterWebhook.getEarlyEndState()).isEqualTo(EarlyEndState.REFUNDED);
    }

    /** The row changed between reading and saving (optimistic locking): the change is applied to it again. */
    @Test
    void aSaveThatLosesAgainstAConcurrentChangeIsRepeated() {
        Subscription subscription = due(EarlyEndState.REVOKED);
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 2400, 0, "eur")));
        when(subscriptionRepository.save(subscription))
                .thenThrow(new ObjectOptimisticLockingFailureException(Subscription.class, 3L))
                .thenAnswer(invocation -> invocation.getArgument(0));

        component.carryOutDueEarlyEnds();

        verify(subscriptionRepository, times(2)).save(subscription);
        assertThat(subscription.getEarlyEndState()).isEqualTo(EarlyEndState.REFUNDED);
    }

    /** Taken back after the list was read: the fresh row has no early end any more. */
    @Test
    void anEarlyEndWithdrawnMeanwhileIsLeftAlone() {
        Subscription read = subscription(EarlyEndState.SCHEDULED, "sub_1");
        Subscription withdrawn = subscription(null, "sub_1");
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of(read));
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(withdrawn));

        component.carryOutDueEarlyEnds();

        verifyNoInteractions(paymentProviderService, accountMailComponent);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void anAccountDeletedMeanwhileIsLeftAlone() {
        Subscription read = subscription(EarlyEndState.SCHEDULED, "sub_1");
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of(read));
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.empty());

        component.carryOutDueEarlyEnds();

        verifyNoInteractions(paymentProviderService);
    }

    /**
     * A refund that is still pending does not show in the order's refunded amount yet; our marker on it shows it was
     * requested, so it is not requested a second time (finding 6).
     */
    @Test
    void retryAfterAPendingRefundDoesNotRefundTwice() {
        Subscription subscription = due(EarlyEndState.REFUNDING);
        subscription.setEarlyEndOrderId("ord_1");
        subscription.setEarlyEndRefundedBefore(0L);
        when(paymentProviderService.getOrder("ord_1"))
                .thenReturn(Optional.of(new ProviderOrder("ord_1", 2400, 0, 2400, "eur")));
        when(paymentProviderService.markedRefundAmount("ord_1", "sub_1")).thenReturn(REFUND);

        component.carryOutDueEarlyEnds();

        verify(paymentProviderService, never()).refund(anyString(), anyLong(), anyString());
        assertThat(saved).containsExactly(EarlyEndState.REFUNDED);
        verify(accountMailComponent).sendCancellationNotice(admin, notice(REFUND, "eur"), CancellationMail.DONE,
                null);
    }

    /** Account deleted with an early end under way: the administrators finish the refund (finding 5). */
    @Test
    void anOpenEarlyEndOfADeletedAccountIsHandedOver() {
        for (EarlyEndState state : List.of(EarlyEndState.SCHEDULED, EarlyEndState.REVOKED, EarlyEndState.REFUNDING)) {
            component.accountDeleted(subscription(state, "sub_1"));
        }

        verify(accountMailComponent, times(3)).sendCancellationNotice(admin, notice(null, null),
                CancellationMail.FAILED, "mail.earlyEnd.reason.accountDeleted");
        assertThat(count("manual")).isEqualTo(3.0);
        verifyNoInteractions(paymentProviderService);
    }

    @Test
    void aFinishedOrMissingEarlyEndOfADeletedAccountNeedsNothing() {
        component.accountDeleted(subscription(null, "sub_1"));
        component.accountDeleted(subscription(EarlyEndState.REFUNDED, "sub_1"));
        component.accountDeleted(subscription(EarlyEndState.MANUAL, "sub_1"));

        verifyNoInteractions(accountMailComponent);
        assertThat(count("manual")).isZero();
    }

    @Test
    void amountWithoutAUsablePeriodIsZero() {
        Subscription subscription = subscription(EarlyEndState.REVOKED, "sub_1");
        subscription.setEarlyEndPeriodStart(subscription.getEarlyEndPeriodEnd());

        assertThat(EarlyEndComponentImpl.amountFor(subscription, new ProviderOrder("ord_1", 2400, 0, 2400, "eur")))
                .isZero();
    }

    /** Due, and the current row is this very object (nothing changes it concurrently). */
    /** 279 of 365 days (2027-02-06 to 2027-11-12) of 2400 cents, rounded: 1835. */
    @Test
    void deletionAfterTheFirstYearRefundsTheRestFromToday() {
        Subscription subscription = subscription(null, "sub_1");
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("txn_1", 2400, 0, 2400, "EUR")));

        component.refundAfterDeletion(subscription, renewed());

        verify(paymentProviderService).refund("txn_1", 1835L, "sub_1");
        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1", TODAY, TODAY,
                LocalDate.of(2027, 11, 12), 1835L, "EUR"), CancellationMail.DONE, null);
        assertThat(count("refunded")).isEqualTo(1.0);
    }

    @Test
    void deletionRefundIsCappedAndSkippedWhenNothingIsLeft() {
        Subscription subscription = subscription(null, "sub_1");
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("txn_1", 2400, 2400, 0, "EUR")));

        component.refundAfterDeletion(subscription, renewed());

        verify(paymentProviderService, never()).refund(any(), anyLong(), any());
        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1", TODAY, TODAY,
                LocalDate.of(2027, 11, 12), 0L, "EUR"), CancellationMail.DONE, null);
    }

    /** First year, an early end of its own, no period data or a paid period that is over: no refund at deletion. */
    @Test
    void deletionWithoutARefundDue() {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-12T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2027-11-12T10:00:00Z");
        component.refundAfterDeletion(subscription(null, "sub_1"), new ProviderSubscription("sub_1", "active", end,
                null, false, "ref-1", start, start, null, 2400L, "EUR"));
        component.refundAfterDeletion(subscription(EarlyEndState.REFUNDED, "sub_1"), renewed());
        component.refundAfterDeletion(subscription(null, "sub_1"),
                new ProviderSubscription("sub_1", "active", end, null, false, "ref-1"));
        component.refundAfterDeletion(subscription(null, "sub_1"), new ProviderSubscription("sub_1", "active",
                OffsetDateTime.parse("2027-02-05T10:00:00Z"), null, false, "ref-1",
                OffsetDateTime.parse("2025-02-05T10:00:00Z"), OffsetDateTime.parse("2026-02-05T10:00:00Z"), null,
                2400L, "EUR"));

        verifyNoInteractions(paymentProviderService, accountMailComponent);
    }

    @Test
    void deletionWithoutAPaidPeriodIsHandedOver() {
        when(paymentProviderService.findLastPeriodOrder("sub_1")).thenReturn(Optional.empty());

        component.refundAfterDeletion(subscription(null, "sub_1"), renewed());

        verify(paymentProviderService, never()).refund(any(), anyLong(), any());
        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1", TODAY, TODAY,
                LocalDate.of(2027, 11, 12), null, null), CancellationMail.FAILED, "mail.earlyEnd.reason.noOrder");
        assertThat(count("manual")).isEqualTo(1.0);
    }

    @Test
    void failedDeletionRefundIsHandedOver() {
        when(paymentProviderService.findLastPeriodOrder("sub_1"))
                .thenReturn(Optional.of(new ProviderOrder("txn_1", 2400, 0, 2400, "EUR")));
        doThrow(new PaymentProviderUnavailableException("down")).when(paymentProviderService)
                .refund(any(), anyLong(), any());

        component.refundAfterDeletion(subscription(null, "sub_1"), renewed());

        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1", TODAY, TODAY,
                LocalDate.of(2027, 11, 12), null, null), CancellationMail.FAILED,
                "mail.earlyEnd.reason.accountDeletedRefund");
        assertThat(count("manual")).isEqualTo(1.0);
        assertThat(count("refunded")).isZero();
    }

    /** Running in its second year: started 2025-11-12, renewed on 2026-11-12 until 2027-11-12. */
    private static ProviderSubscription renewed() {
        return new ProviderSubscription("sub_1", "active", OffsetDateTime.parse("2027-11-12T10:00:00Z"), null, false,
                "ref-1", OffsetDateTime.parse("2025-11-12T10:00:00Z"), OffsetDateTime.parse("2026-11-12T10:00:00Z"),
                null, 2400L, "EUR");
    }

    private Subscription due(EarlyEndState state) {
        Subscription subscription = subscription(state, "sub_1");
        when(subscriptionRepository.findEarlyEndsDueBefore(any(), eq(TODAY))).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(subscription));
        return subscription;
    }

    private Subscription subscription(EarlyEndState state, String externalId) {
        return Subscription.builder().id(3L).user(owner).externalId(externalId).status(SubscriptionStatus.CANCELED)
                .earlyEndState(state)
                .earlyEndReceivedOn(LocalDate.of(2027, 1, 5))
                .earlyEndOn(LocalDate.of(2027, 2, 5))
                .earlyEndPeriodStart(LocalDate.of(2026, 11, 12))
                .earlyEndPeriodEnd(LocalDate.of(2027, 11, 12))
                .build();
    }

    private static CancellationNotice notice(Long refund, String currency) {
        return new CancellationNotice(7L, "sub_1", LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5),
                LocalDate.of(2027, 11, 12), refund, currency);
    }

    private double count(String result) {
        return meterRegistry.counter(EarlyEndComponentImpl.METRIC, "result", result).count();
    }
}
