package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.RetentionProperties;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRetentionComponentImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 3, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private IAccessComponent accessComponent;
    @Mock
    private IAccountMailComponent accountMailComponent;
    @Mock
    private IAccountDataDeletionComponent accountDataDeletionComponent;
    @Mock
    private ISessionComponent sessionComponent;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AccountRetentionComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new AccountRetentionComponentImpl(appUserRepository, accessComponent, accountMailComponent,
                accountDataDeletionComponent, sessionComponent,
                new RetentionProperties(Period.ofMonths(12), Period.ofDays(30), 2),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), meterRegistry);
        lenient().when(accountMailComponent.sendDeletionNotice(any(), any())).thenReturn(true);
        lenient().when(accountDataDeletionComponent.deleteWithAllData(any(), any())).thenReturn(true);
    }

    @Test
    void countersAreRegisteredWithZero() {
        for (String action : List.of("notice_sent", "notice_deferred", "deleted", "kept", "failed")) {
            assertThat(meterRegistry.find("bm.accounts.retention").tag("action", action).counter()).isNotNull();
        }
    }

    /** Only accounts whose trial started long enough ago (plus a few days of slack) or with a pending notice. */
    @Test
    void asksOnlyForUsersWhoseTrialStartedLongEnoughAgo() {
        component.applyRetention();

        verify(appUserRepository).findRetentionCandidates(UserRole.USER, LocalDate.of(2025, 12, 19));
    }

    @Test
    void nothingHappensBeforeTheNoticePeriod() {
        AppUser user = candidates(user(1L, null)).getFirst();
        expired(user, LocalDate.of(2026, 1, 10)); // due 2027-01-10, notice from 2026-12-11

        component.applyRetention();

        verifyNoInteractions(accountMailComponent, accountDataDeletionComponent);
        verify(appUserRepository, never()).setDeletionNoticeSentAt(any(), any());
    }

    @Test
    void sendsTheNoticeOnceTheNoticePeriodStarted() {
        AppUser user = candidates(user(1L, null)).getFirst();
        expired(user, LocalDate.of(2025, 12, 12)); // due 2026-12-12 = today + 30 days

        component.applyRetention();

        verify(accountMailComponent).sendDeletionNotice(user, LocalDate.of(2026, 12, 12));
        verify(appUserRepository).setDeletionNoticeSentAt(1L, NOW);
        verify(appUserRepository, never()).save(any());
        verifyNoInteractions(accountDataDeletionComponent);
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "notice_sent").count()).isEqualTo(1.0);
    }

    /** The mail server did not accept the notice: it does not count as sent, so no deletion follows without it. */
    @Test
    void aNoticeTheMailServerRejectedIsNotCountedAsSent() {
        AppUser user = candidates(user(1L, null)).getFirst();
        expired(user, LocalDate.of(2025, 12, 12));
        when(accountMailComponent.sendDeletionNotice(user, LocalDate.of(2026, 12, 12))).thenReturn(false);

        component.applyRetention();

        verify(appUserRepository, never()).setDeletionNoticeSentAt(any(), any());
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "notice_sent").count()).isZero();
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "failed").count()).isEqualTo(1.0);
    }

    /** A notice that goes out late (e.g. deferred by the limit) still gives the full notice period. */
    @Test
    void lateNoticeNamesADayAFullNoticePeriodAhead() {
        AppUser user = candidates(user(1L, null)).getFirst();
        expired(user, LocalDate.of(2025, 10, 1)); // due 2026-10-01, already over

        component.applyRetention();

        verify(accountMailComponent).sendDeletionNotice(user, LocalDate.of(2026, 12, 12));
        verifyNoInteractions(accountDataDeletionComponent);
    }

    @Test
    void deletesWhenDueAndTheNoticePeriodIsOver() {
        AppUser user = candidates(user(1L, LocalDateTime.of(2026, 10, 13, 3, 30))).getFirst();
        expired(user, LocalDate.of(2025, 11, 1)); // due 2026-11-01; notice + 30 days = 2026-11-12

        component.applyRetention();

        verify(accountDataDeletionComponent).deleteWithAllData(eq(user), any());
        verify(sessionComponent).invalidateSessionsOf(1L);
        verifyNoInteractions(accountMailComponent);
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "deleted").count()).isEqualTo(1.0);
    }

    /**
     * The condition checked on the locked account right before deleting: the same pending notice and still due. A
     * login since then withdrew the notice; a new subscription ended the expiry.
     */
    @Test
    void deletionIsCheckedAgainOnTheCurrentAccount() {
        LocalDateTime noticeSentAt = LocalDateTime.of(2026, 10, 13, 3, 30);
        AppUser user = candidates(user(1L, noticeSentAt)).getFirst();
        expired(user, LocalDate.of(2025, 11, 1));
        ArgumentCaptor<Predicate<AppUser>> stillDue = ArgumentCaptor.captor();

        component.applyRetention();

        verify(accountDataDeletionComponent).deleteWithAllData(eq(user), stillDue.capture());
        assertThat(stillDue.getValue().test(user)).isTrue();

        AppUser loggedIn = user(1L, null);
        loggedIn.setKeepUntil(LocalDate.of(2027, 11, 12));
        assertThat(stillDue.getValue().test(loggedIn)).isFalse();

        AppUser subscribed = user(1L, noticeSentAt);
        when(accessComponent.accessOf(subscribed)).thenReturn(new Access(AccessStatus.ACTIVE, TODAY, true));
        assertThat(stillDue.getValue().test(subscribed)).isFalse();
    }

    @Test
    void anAccountThatChangedBeforeTheDeletionIsKept() {
        AppUser user = candidates(user(1L, LocalDateTime.of(2026, 10, 13, 3, 30))).getFirst();
        expired(user, LocalDate.of(2025, 11, 1));
        when(accountDataDeletionComponent.deleteWithAllData(eq(user), any())).thenReturn(false);

        component.applyRetention();

        verifyNoInteractions(sessionComponent);
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "deleted").count()).isZero();
    }

    /** The candidate list is a snapshot: each account is read again, e.g. a login withdrew the notice since. */
    @Test
    void eachCandidateIsReadAgainBeforeItIsHandled() {
        AppUser listed = user(1L, LocalDateTime.of(2026, 10, 13, 3, 30));
        when(appUserRepository.findRetentionCandidates(any(), any())).thenReturn(List.of(listed, user(2L, null)));
        AppUser current = user(1L, null);
        current.setKeepUntil(LocalDate.of(2027, 11, 12));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(current));
        when(appUserRepository.findById(2L)).thenReturn(Optional.empty());
        expired(current, LocalDate.of(2025, 11, 1));

        component.applyRetention();

        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
    }

    @Test
    void neverDeletesBeforeTheNoticePeriodIsOver() {
        AppUser user = candidates(user(1L, LocalDateTime.of(2026, 10, 20, 3, 30))).getFirst();
        expired(user, LocalDate.of(2025, 11, 1)); // due, but notice only 23 days ago

        component.applyRetention();

        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
    }

    @Test
    void neverDeletesBeforeTheDueDay() {
        AppUser user = candidates(user(1L, LocalDateTime.of(2026, 10, 1, 3, 30))).getFirst();
        expired(user, LocalDate.of(2025, 12, 1)); // notice period over, but due only on 2026-12-01

        component.applyRetention();

        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
    }

    /** E.g. a new subscription after the notice: the notice is withdrawn, a later expiry sends a new one. */
    @Test
    void withdrawsThePendingNoticeOfAnAccountThatIsNoLongerExpired() {
        AppUser user = candidates(user(1L, LocalDateTime.of(2026, 9, 1, 3, 30))).getFirst();
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.ACTIVE, TODAY.plusMonths(1), true));

        component.applyRetention();

        verify(appUserRepository).setDeletionNoticeSentAt(1L, null);
        verify(appUserRepository, never()).save(any());
        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
    }

    @Test
    void accountsInTrialOrWithoutKnownEndAreLeftAlone() {
        List<AppUser> users = candidates(user(1L, null), user(2L, null));
        when(accessComponent.accessOf(users.get(0))).thenReturn(new Access(AccessStatus.TRIAL, TODAY, false));
        when(accessComponent.accessOf(users.get(1))).thenReturn(new Access(AccessStatus.EXPIRED, null, false));

        component.applyRetention();

        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
        verify(appUserRepository, never()).setDeletionNoticeSentAt(any(), any());
    }

    @Test
    void aLoginAfterTheNoticeKeepsTheAccountUntilKeepUntil() {
        AppUser user = user(1L, null);
        user.setKeepUntil(LocalDate.of(2027, 6, 1));
        candidates(user);
        expired(user, LocalDate.of(2025, 1, 1)); // would be long overdue without keepUntil

        component.applyRetention();

        verifyNoInteractions(accountDataDeletionComponent, accountMailComponent);
    }

    @Test
    void keepUntilIsTheDueDayOnceItCame() {
        AppUser user = user(1L, null);
        user.setKeepUntil(LocalDate.of(2026, 12, 12));
        candidates(user);
        expired(user, LocalDate.of(2025, 1, 1));

        component.applyRetention();

        verify(accountMailComponent).sendDeletionNotice(user, LocalDate.of(2026, 12, 12));
    }

    @Test
    void sendsAtMostTheDailyLimitOfNotices() {
        List<AppUser> users = candidates(user(1L, null), user(2L, null), user(3L, null));
        users.forEach(user -> expired(user, LocalDate.of(2025, 10, 1)));

        component.applyRetention();

        verify(accountMailComponent).sendDeletionNotice(users.get(0), LocalDate.of(2026, 12, 12));
        verify(accountMailComponent).sendDeletionNotice(users.get(1), LocalDate.of(2026, 12, 12));
        verify(accountMailComponent, never()).sendDeletionNotice(users.get(2), LocalDate.of(2026, 12, 12));
        verify(appUserRepository, never()).setDeletionNoticeSentAt(eq(3L), any());
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "notice_deferred").count())
                .isEqualTo(1.0);
    }

    @Test
    void aFailingAccountDoesNotStopTheOthers() {
        LocalDateTime noticeSentAt = LocalDateTime.of(2026, 9, 1, 3, 30);
        List<AppUser> users = candidates(user(1L, noticeSentAt), user(2L, noticeSentAt));
        users.forEach(user -> expired(user, LocalDate.of(2025, 10, 1)));
        doThrow(new PaymentProviderUnavailableException("down")).when(accountDataDeletionComponent)
                .deleteWithAllData(eq(users.get(0)), any());

        component.applyRetention();

        verify(accountDataDeletionComponent).deleteWithAllData(eq(users.get(1)), any());
        verify(sessionComponent, never()).invalidateSessionsOf(1L);
        verify(sessionComponent).invalidateSessionsOf(2L);
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "failed").count()).isEqualTo(1.0);
    }

    @Test
    void loginAfterTheNoticeKeepsTheAccountForAnotherPeriod() {
        AppUser user = user(1L, LocalDateTime.of(2026, 11, 1, 3, 30));

        component.keepAfterLogin(user);

        assertThat(user.getDeletionNoticeSentAt()).isNull();
        assertThat(user.getKeepUntil()).isEqualTo(LocalDate.of(2027, 11, 12));
        verify(appUserRepository).save(user);
        assertThat(meterRegistry.counter("bm.accounts.retention", "action", "kept").count()).isEqualTo(1.0);
    }

    /** Without a notice nothing is written: login times are not recorded. */
    @Test
    void loginWithoutNoticeChangesNothing() {
        AppUser user = user(1L, null);

        component.keepAfterLogin(user);

        assertThat(user.getKeepUntil()).isNull();
        verifyNoInteractions(appUserRepository);
    }

    /** Listed, and read again unchanged. */
    private List<AppUser> candidates(AppUser... users) {
        when(appUserRepository.findRetentionCandidates(any(), any())).thenReturn(List.of(users));
        for (AppUser user : users) {
            lenient().when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
        }
        return List.of(users);
    }

    private void expired(AppUser user, LocalDate accessUntil) {
        when(accessComponent.accessOf(user)).thenReturn(new Access(AccessStatus.EXPIRED, accessUntil, false));
    }

    private static AppUser user(Long id, LocalDateTime deletionNoticeSentAt) {
        return AppUser.builder()
                .id(id)
                .email("rider" + id + "@example.org")
                .role(UserRole.USER)
                .emailVerified(true)
                .deletionNoticeSentAt(deletionNoticeSentAt)
                .build();
    }
}
