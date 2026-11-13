package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.SubscriptionProperties;
import de.chriswohlbrecht.maintenance.exception.SubscriptionRequiredException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.SubscriptionRepository;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionProvider;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalAccessComponentImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final int TRIAL_DAYS = 10;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ICurrentUserComponent currentUserComponent;

    private PortalAccessComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new PortalAccessComponentImpl(subscriptionRepository, currentUserComponent,
                new SubscriptionProperties(TRIAL_DAYS),
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void trialOnItsFirstDay() {
        Access access = component.accessOf(user(TODAY));

        assertThat(access).isEqualTo(new Access(AccessStatus.TRIAL, TODAY.plusDays(TRIAL_DAYS - 1L), false));
    }

    @Test
    void trialOnItsLastDay() {
        Access access = component.accessOf(user(TODAY.minusDays(TRIAL_DAYS - 1L)));

        assertThat(access.status()).isEqualTo(AccessStatus.TRIAL);
        assertThat(access.until()).isEqualTo(TODAY);
    }

    @Test
    void expiredTheDayAfterTheTrial() {
        Access access = component.accessOf(user(TODAY.minusDays(TRIAL_DAYS)));

        assertThat(access).isEqualTo(new Access(AccessStatus.EXPIRED, TODAY.minusDays(1), false));
    }

    @Test
    void expiredWithoutTrialStart() {
        Access access = component.accessOf(user(null));

        assertThat(access).isEqualTo(new Access(AccessStatus.EXPIRED, null, false));
    }

    @Test
    void activeWhileThePaidPeriodLastsEvenIfCancelled() {
        AppUser user = user(TODAY.minusDays(100));
        subscriptions(user, subscription(SubscriptionStatus.CANCELED, TODAY));

        assertThat(component.accessOf(user)).isEqualTo(new Access(AccessStatus.ACTIVE, TODAY, true));
    }

    @Test
    void activeWhileThePaymentIsRetriedAfterThePeriodEnded() {
        AppUser user = user(TODAY.minusDays(100));
        subscriptions(user, subscription(SubscriptionStatus.PAST_DUE, TODAY.minusDays(3)));

        assertThat(component.accessOf(user).status()).isEqualTo(AccessStatus.ACTIVE);
    }

    /**
     * A lost webhook must not grant access forever (backend review, finding 9): a subscription reported running whose
     * paid period is over by more than the tolerance only counts while a recent sync confirms it.
     */
    @Test
    void runningButLongOverdueWithoutConfirmationIsExpired() {
        AppUser user = user(TODAY.minusDays(400));
        Subscription stale = subscription(SubscriptionStatus.ACTIVE,
                TODAY.minusDays(PortalAccessComponentImpl.RUNNING_TOLERANCE_DAYS + 1L));
        stale.setSyncedAt(TODAY.minusDays(30).atStartOfDay());
        subscriptions(user, stale);

        assertThat(component.accessOf(user).status()).isEqualTo(AccessStatus.EXPIRED);
    }

    /** The provider still retries the payment and the nightly re-sync confirmed that: access goes on. */
    @Test
    void runningAndOverdueButRecentlyConfirmedIsActive() {
        AppUser user = user(TODAY.minusDays(400));
        Subscription retried = subscription(SubscriptionStatus.PAST_DUE, TODAY.minusDays(20));
        retried.setSyncedAt(TODAY.minusDays(PortalAccessComponentImpl.RUNNING_TOLERANCE_DAYS).atTime(3, 30));
        subscriptions(user, retried);

        assertThat(component.accessOf(user).status()).isEqualTo(AccessStatus.ACTIVE);
    }

    @Test
    void runningWithoutAnyPeriodOrConfirmationIsNoAccess() {
        AppUser user = user(TODAY.minusDays(400));
        subscriptions(user, subscription(SubscriptionStatus.ACTIVE, null));

        assertThat(component.accessOf(user).status()).isEqualTo(AccessStatus.EXPIRED);
    }

    /** The admin list: one query for all accounts, each gets its own access. */
    @Test
    void accessOfManyAccountsUsesOneQuery() {
        AppUser subscriber = user(TODAY.minusDays(400));
        AppUser trial = AppUser.builder().id(8L).email("new@example.org").role(UserRole.USER).emailVerified(true)
                .trialStartedOn(TODAY).build();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, TODAY.plusDays(30));
        subscription.setUser(subscriber);
        when(subscriptionRepository.findAllByUser_IdIn(List.of(7L, 8L))).thenReturn(List.of(subscription));

        Map<Long, Access> access = component.accessOf(List.of(subscriber, trial));

        assertThat(access.get(7L)).isEqualTo(new Access(AccessStatus.ACTIVE, TODAY.plusDays(30), true));
        assertThat(access.get(8L).status()).isEqualTo(AccessStatus.TRIAL);
        verify(subscriptionRepository, never()).findAllByUser_Id(any());
    }

    @Test
    void subscriptionWinsOverARunningTrial() {
        AppUser user = user(TODAY);
        subscriptions(user, subscription(SubscriptionStatus.ACTIVE, TODAY.plusDays(365)));

        assertThat(component.accessOf(user)).isEqualTo(new Access(AccessStatus.ACTIVE, TODAY.plusDays(365), true));
    }

    @Test
    void expiredAfterTheSubscriptionEndedReportsItsLastPaidDay() {
        AppUser user = user(TODAY.minusDays(400));
        subscriptions(user,
                subscription(SubscriptionStatus.ENDED, TODAY.minusDays(30)),
                subscription(SubscriptionStatus.ENDED, TODAY.minusDays(5)));

        assertThat(component.accessOf(user)).isEqualTo(new Access(AccessStatus.EXPIRED, TODAY.minusDays(5), true));
    }

    @Test
    void trialAfterAnEndedSubscriptionIsStillReported() {
        AppUser user = user(TODAY.minusDays(2));
        subscriptions(user, subscription(SubscriptionStatus.ENDED, null));

        assertThat(component.accessOf(user)).isEqualTo(
                new Access(AccessStatus.TRIAL, TODAY.minusDays(2).plusDays(TRIAL_DAYS - 1L), true));
    }

    @Test
    void administratorsAreNeverRestricted() {
        AppUser admin = AppUser.builder().id(1L).email("admin@example.org").role(UserRole.ADMIN).build();

        assertThat(component.accessOf(admin)).isEqualTo(Access.full(false));
    }

    @Test
    void requireWriteAccessPassesDuringTheTrial() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user(TODAY));

        assertThatCode(component::requireWriteAccess).doesNotThrowAnyException();
    }

    @Test
    void requireWriteAccessFailsForAReadOnlyAccount() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user(TODAY.minusDays(TRIAL_DAYS)));

        assertThatThrownBy(component::requireWriteAccess).isInstanceOf(SubscriptionRequiredException.class);
    }

    /** An import creates many entries in one request; the account is only checked once. */
    @Test
    void requireWriteAccessChecksOncePerRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(currentUserComponent.getCurrentUser()).thenReturn(user(TODAY));

        Access first = component.requireWriteAccess();
        assertThat(component.requireWriteAccess()).isSameAs(first);

        verify(currentUserComponent, times(1)).getCurrentUser();
    }

    @Test
    void aRefusedWriteAccessIsCheckedAgain() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(currentUserComponent.getCurrentUser()).thenReturn(user(null));

        assertThatThrownBy(component::requireWriteAccess).isInstanceOf(SubscriptionRequiredException.class);
        assertThatThrownBy(component::requireWriteAccess).isInstanceOf(SubscriptionRequiredException.class);

        verify(currentUserComponent, times(2)).getCurrentUser();
    }

    private static AppUser user(LocalDate trialStartedOn) {
        return AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).emailVerified(true)
                .trialStartedOn(trialStartedOn).build();
    }

    private void subscriptions(AppUser user, Subscription... subscriptions) {
        when(subscriptionRepository.findAllByUser_Id(user.getId())).thenReturn(List.of(subscriptions));
    }

    private static Subscription subscription(SubscriptionStatus status, LocalDate paidUntil) {
        return Subscription.builder().provider(SubscriptionProvider.POLAR).externalId("sub_1").status(status)
                .paidUntil(paidUntil).build();
    }
}
