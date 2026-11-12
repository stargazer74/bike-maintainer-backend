package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationMail;
import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.EarlyEndProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.EarlyEndState;
import de.chriswohlbrecht.maintenance.persistence.type.SubscriptionStatus;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancellationNoticeComponentImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-05T09:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final OffsetDateTime STARTED = OffsetDateTime.parse("2025-11-12T10:00:00Z");
    private static final OffsetDateTime RENEWED = OffsetDateTime.parse("2026-11-12T10:00:00Z");
    private static final OffsetDateTime PERIOD_END = OffsetDateTime.parse("2027-11-12T10:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private IAccountMailComponent accountMailComponent;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AppUser owner = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).build();
    private final Subscription entity = Subscription.builder().id(3L).user(owner).externalId("sub_1")
            .status(SubscriptionStatus.CANCELED).build();
    private CancellationNoticeComponentImpl component;

    @BeforeEach
    void setUp() {
        component = component(false);
    }

    private CancellationNoticeComponentImpl component(boolean earlyEnd) {
        return new CancellationNoticeComponentImpl(appUserRepository, accountMailComponent,
                new EarlyEndProperties(earlyEnd, 3), CLOCK, meterRegistry);
    }

    @Test
    void countersAreRegisteredWithZero() {
        for (String term : List.of("first_year", "after_first_year", "unknown")) {
            assertThat(meterRegistry.find(CancellationNoticeComponentImpl.METRIC).tag("term", term).counter())
                    .isNotNull();
        }
    }

    /** First year: minimum term, Polar ends it with the paid year, no refund – nothing to do. */
    @Test
    void cancellationInTheFirstYearNeedsNothing() {
        component.cancellationReceived(owner, entity, subscription(STARTED, STARTED.plusYears(1),
                OffsetDateTime.parse("2026-03-01T12:00:00Z"), 2400L));

        verifyNoInteractions(accountMailComponent, appUserRepository);
        assertThat(meterRegistry.counter(CancellationNoticeComponentImpl.METRIC, "term", "first_year").count())
                .isEqualTo(1.0);
    }

    @Test
    void cancellationAfterTheFirstYearIsMailedToEveryActiveAdmin() {
        AppUser first = admin(1L);
        AppUser second = admin(2L);
        when(appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(List.of(first, second));

        component.cancellationReceived(owner, entity, subscription(STARTED, PERIOD_END,
                OffsetDateTime.parse("2027-01-05T08:00:00Z"), 2400L));

        // Received 2027-01-05, ends 2027-02-05; 280 of 365 days of the period 2026-11-12…2027-11-12 are refunded.
        CancellationNotice expected = new CancellationNotice(7L, "sub_1", LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 2, 5), LocalDate.of(2027, 11, 12), 1841L, "eur");
        verify(accountMailComponent).sendCancellationNotice(first, expected, CancellationMail.MANUAL, null);
        verify(accountMailComponent).sendCancellationNotice(second, expected, CancellationMail.MANUAL, null);
        assertThat(meterRegistry.counter(CancellationNoticeComponentImpl.METRIC, "term", "after_first_year").count())
                .isEqualTo(1.0);
        assertThat(entity.getEarlyEndState()).isNull();
    }

    @Test
    void withEarlyEndTheEndIsPlannedOnTheSubscription() {
        AppUser admin = admin(1L);
        when(appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(List.of(admin));
        component = component(true);
        entity.setEarlyEndAttempts(2);
        entity.setEarlyEndOrderId("old");

        component.cancellationReceived(owner, entity, subscription(STARTED, PERIOD_END,
                OffsetDateTime.parse("2027-01-05T08:00:00Z"), 2400L));

        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1",
                LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5), LocalDate.of(2027, 11, 12), 1841L, "eur"),
                CancellationMail.SCHEDULED, null);
        assertThat(entity.getEarlyEndState()).isEqualTo(EarlyEndState.SCHEDULED);
        assertThat(entity.getEarlyEndReceivedOn()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(entity.getEarlyEndOn()).isEqualTo(LocalDate.of(2027, 2, 5));
        assertThat(entity.getEarlyEndPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(entity.getEarlyEndPeriodEnd()).isEqualTo(LocalDate.of(2027, 11, 12));
        assertThat(entity.getEarlyEndAttempts()).isZero();
        assertThat(entity.getEarlyEndOrderId()).isNull();
        assertThat(meterRegistry.counter(EarlyEndComponentImpl.METRIC, "result", "scheduled").count())
                .isEqualTo(1.0);
    }

    /** An early end under way (or handed over) is not planned again by a repeated cancellation. */
    @Test
    void anEarlyEndUnderWayIsNotPlannedAgain() {
        component = component(true);
        entity.setEarlyEndState(EarlyEndState.REVOKED);
        entity.setEarlyEndOn(LocalDate.of(2027, 1, 1));

        component.cancellationReceived(owner, entity, subscription(STARTED, PERIOD_END,
                OffsetDateTime.parse("2027-01-05T08:00:00Z"), 2400L));

        verifyNoInteractions(accountMailComponent, appUserRepository);
        assertThat(entity.getEarlyEndOn()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void withdrawalDropsAPlannedEarlyEnd() {
        entity.setEarlyEndState(EarlyEndState.SCHEDULED);
        entity.setEarlyEndReceivedOn(LocalDate.of(2027, 1, 5));
        entity.setEarlyEndOn(LocalDate.of(2027, 2, 5));
        entity.setEarlyEndPeriodStart(LocalDate.of(2026, 11, 12));
        entity.setEarlyEndPeriodEnd(LocalDate.of(2027, 11, 12));

        component.cancellationWithdrawn(entity);

        assertThat(entity.getEarlyEndState()).isNull();
        assertThat(entity.getEarlyEndReceivedOn()).isNull();
        assertThat(entity.getEarlyEndOn()).isNull();
        assertThat(entity.getEarlyEndPeriodStart()).isNull();
        assertThat(entity.getEarlyEndPeriodEnd()).isNull();
        assertThat(meterRegistry.counter(EarlyEndComponentImpl.METRIC, "result", "withdrawn").count())
                .isEqualTo(1.0);
    }

    /** Once the subscription is revoked, taking the cancellation back changes nothing any more. */
    @Test
    void withdrawalAfterTheStartChangesNothing() {
        entity.setEarlyEndState(EarlyEndState.REVOKED);
        entity.setEarlyEndOn(LocalDate.of(2027, 2, 5));

        component.cancellationWithdrawn(entity);

        assertThat(entity.getEarlyEndState()).isEqualTo(EarlyEndState.REVOKED);
        assertThat(entity.getEarlyEndOn()).isEqualTo(LocalDate.of(2027, 2, 5));
    }

    /** Without the time of the cancellation the webhook's arrival counts; without a price the refund is unknown. */
    @Test
    void missingCancellationTimeAndPriceAreFilledOrLeftOpen() {
        AppUser admin = admin(1L);
        when(appUserRepository.findAllByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(List.of(admin));

        component.cancellationReceived(owner, entity, subscription(STARTED, PERIOD_END, null, null));

        verify(accountMailComponent).sendCancellationNotice(admin, new CancellationNotice(7L, "sub_1",
                LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5), LocalDate.of(2027, 11, 12), null, "eur"),
                CancellationMail.MANUAL, null);
    }

    /** The paid period ends within the month anyway: Polar's own end is already in time, nothing to refund. */
    @Test
    void periodEndingWithinTheMonthNeedsNothing() {
        component.cancellationReceived(owner, entity, subscription(STARTED, OffsetDateTime.parse("2027-02-01T10:00:00Z"),
                OffsetDateTime.parse("2027-01-05T08:00:00Z"), 2400L));

        verifyNoInteractions(accountMailComponent, appUserRepository);
    }

    @Test
    void withoutPeriodDataNothingIsJudged() {
        component.cancellationReceived(owner, entity, new ProviderSubscription("sub_1", "active", PERIOD_END, null, true,
                "ref-1"));

        verifyNoInteractions(accountMailComponent, appUserRepository);
        assertThat(meterRegistry.counter(CancellationNoticeComponentImpl.METRIC, "term", "unknown").count())
                .isEqualTo(1.0);
    }

    @Test
    void refundIsProRataAndRoundedToTheCent() {
        LocalDate start = LocalDate.of(2026, 11, 12);
        LocalDate end = LocalDate.of(2027, 11, 12);
        assertThat(CancellationNoticeComponentImpl.refund(2400L, start, LocalDate.of(2027, 2, 5), end))
                .isEqualTo(1841L);
        assertThat(CancellationNoticeComponentImpl.refund(2400L, start, end, end)).isZero();
        assertThat(CancellationNoticeComponentImpl.refund(null, start, end, end)).isNull();
        assertThat(CancellationNoticeComponentImpl.refund(2400L, end, end, end)).isNull();
    }

    private static ProviderSubscription subscription(OffsetDateTime startedAt, OffsetDateTime periodEnd,
                                                     OffsetDateTime canceledAt, Long amount) {
        OffsetDateTime periodStart = periodEnd.minusYears(1).isBefore(startedAt) ? startedAt : periodEnd.minusYears(1);
        return new ProviderSubscription("sub_1", "active", periodEnd, null, true, "ref-1", startedAt, periodStart,
                canceledAt, amount, "eur");
    }

    private static AppUser admin(Long id) {
        return AppUser.builder().id(id).email("admin" + id + "@example.org").role(UserRole.ADMIN).active(true).build();
    }
}
