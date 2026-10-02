package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MaintenanceTaskStatusHelperTest {

    private static final LocalDate NOW = LocalDate.of(2025, 1, 1);

    private MaintenanceTaskStatusHelper calculator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        calculator = new MaintenanceTaskStatusHelper(clock);
    }

    @Test
    void neverPerformed_withoutAnyAnchor_returnsDueSoonWithoutProgress() {
        MaintenanceTask task = taskBuilder().build();

        MaintenanceTaskStatusResult result = calculator.compute(task, 500, null, List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.progress()).isNull();
        assertThat(result.remainingFraction()).isEqualTo(0.0);
        assertThat(result.kmRemaining()).isNull();
        assertThat(result.daysRemaining()).isNull();
        assertThat(result.lastServiceMileage()).isNull();
        assertThat(result.lastServiceDate()).isNull();
    }

    @Test
    void neverPerformed_withFirstDueKm_evaluatesKmAxisFromZero() {
        MaintenanceTask task = taskBuilder().firstDueKm(1000).build();

        MaintenanceTaskStatusResult result = calculator.compute(task, 500, null, List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.kmRemaining()).isEqualTo(500);
        assertThat(result.daysRemaining()).isNull();
        assertThat(result.progress()).isEqualTo(0.5);
        assertThat(result.remainingFraction()).isEqualTo(0.5);
    }

    @Test
    void neverPerformed_withoutFirstDueKm_fallsBackToIntervalKm() {
        MaintenanceTask task = taskBuilder().intervalKm(2000).build();

        MaintenanceTaskStatusResult result = calculator.compute(task, 2500, null, List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OVERDUE);
        assertThat(result.kmRemaining()).isEqualTo(-500);
    }

    @Test
    void neverPerformed_withFirstDueMonths_evaluatesTimeAxisFromVehicleFirstRegistrationDate() {
        // The fix: a purely time-based task that was never performed used to be hardcoded to
        // DUE_SOON with no numbers — it should instead measure from the vehicle's actual
        // first registration date (Erstzulassung), which is mandatory on every vehicle.
        MaintenanceTask task = taskBuilder().firstDueMonths(1).build();

        MaintenanceTaskStatusResult result =
                calculator.compute(task, 0, LocalDate.of(2024, 12, 15), List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.kmRemaining()).isNull();
        assertThat(result.daysRemaining()).isEqualTo(13);
        assertThat(result.progress()).isCloseTo(0.5585, within(0.001));
    }

    @Test
    void neverPerformed_withoutFirstDueMonths_fallsBackToIntervalMonths() {
        MaintenanceTask task = taskBuilder().intervalMonths(1).build();

        MaintenanceTaskStatusResult result =
                calculator.compute(task, 0, LocalDate.of(2024, 12, 15), List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.daysRemaining()).isEqualTo(13);
    }

    @Test
    void neverPerformed_withoutVehicleFirstRegistrationDate_hasNoTimeAxis() {
        // Defensive boundary: the DB guarantees this is always set, but the helper shouldn't
        // blow up if it's ever null (e.g. a hand-built entity in a test).
        MaintenanceTask task = taskBuilder().firstDueMonths(1).build();

        MaintenanceTaskStatusResult result = calculator.compute(task, 0, null, List.of());

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.daysRemaining()).isNull();
        assertThat(result.remainingFraction()).isEqualTo(0.0);
    }

    @Test
    void neverPerformed_withBothAnchors_combinesAxesAndWorstWins() {
        MaintenanceTask task = taskBuilder()
                .firstDueKm(2000) // due-soon-ish
                .firstDueMonths(1)
                .build();

        MaintenanceTaskStatusResult result =
                calculator.compute(task, 1500, LocalDate.of(2024, 11, 1), List.of()); // long overdue on time

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OVERDUE);
        // Only the worse (time) axis's remaining is surfaced — the frontend label needs exactly
        // one unambiguous number to show, same as when only one axis exists.
        assertThat(result.kmRemaining()).isNull();
        assertThat(result.daysRemaining()).isNotNull();
        assertThat(result.daysRemaining()).isLessThan(0);
    }

    @Test
    void oneTimeTaskWithLog_returnsDone() {
        MaintenanceTask task = taskBuilder().oneTime(true).build();
        MaintenanceLog log = logWith(LocalDate.of(2024, 6, 1), 1234);

        MaintenanceTaskStatusResult result = calculator.compute(task, 2000, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DONE);
        assertThat(result.lastServiceMileage()).isEqualTo(1234);
        assertThat(result.lastServiceDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(result.progress()).isNull();
        assertThat(result.remainingFraction()).isNull();
        assertThat(result.kmRemaining()).isNull();
        assertThat(result.daysRemaining()).isNull();
    }

    @Test
    void performed_kmOverdue() {
        MaintenanceTask task = taskBuilder().intervalKm(1000).build();
        MaintenanceLog log = logWith(LocalDate.of(2020, 1, 1), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 1500, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OVERDUE);
        assertThat(result.kmRemaining()).isEqualTo(-500);
        assertThat(result.progress()).isEqualTo(1.0);
    }

    @Test
    void performed_kmDueSoon() {
        MaintenanceTask task = taskBuilder().intervalKm(1000).build();
        MaintenanceLog log = logWith(LocalDate.of(2020, 1, 1), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 600, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.kmRemaining()).isEqualTo(400);
    }

    @Test
    void performed_kmOk() {
        MaintenanceTask task = taskBuilder().intervalKm(5000).build();
        MaintenanceLog log = logWith(LocalDate.of(2020, 1, 1), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 100, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OK);
        assertThat(result.kmRemaining()).isEqualTo(4900);
    }

    @Test
    void performed_timeOverdue() {
        MaintenanceTask task = taskBuilder().intervalMonths(1).build();
        MaintenanceLog log = logWith(LocalDate.of(2024, 1, 1), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 0, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OVERDUE);
        assertThat(result.daysRemaining()).isLessThan(0);
    }

    @Test
    void performed_timeDueSoon() {
        MaintenanceTask task = taskBuilder().intervalMonths(1).build();
        MaintenanceLog log = logWith(NOW.minusDays(20), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 0, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.DUE_SOON);
        assertThat(result.daysRemaining()).isEqualTo(10);
    }

    @Test
    void performed_timeOk() {
        MaintenanceTask task = taskBuilder().intervalMonths(2).build();
        MaintenanceLog log = logWith(NOW.minusDays(5), 0);

        MaintenanceTaskStatusResult result = calculator.compute(task, 0, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OK);
        assertThat(result.daysRemaining()).isEqualTo(56);
    }

    @Test
    void performed_bothAxesPresent_onlyWorstAxisRemainingIsPopulated() {
        MaintenanceTask task = taskBuilder().intervalKm(5000).intervalMonths(1).build();
        MaintenanceLog log = logWith(LocalDate.of(2024, 1, 1), 0); // long overdue on time, fine on km

        MaintenanceTaskStatusResult result = calculator.compute(task, 100, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OVERDUE);
        assertThat(result.kmRemaining()).isNull();
        assertThat(result.daysRemaining()).isLessThan(0);
    }

    @Test
    void performed_bothAxesPresent_kmWinsTieWhenSeveritiesAreEqual() {
        MaintenanceTask task = taskBuilder().intervalKm(5000).intervalMonths(12).build();
        MaintenanceLog log = logWith(NOW.minusDays(1), 100); // both axes comfortably OK

        MaintenanceTaskStatusResult result = calculator.compute(task, 200, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OK);
        assertThat(result.kmRemaining()).isNotNull();
        assertThat(result.daysRemaining()).isNull();
    }

    @Test
    void performed_withoutAnyInterval_returnsOkWithoutProgress() {
        MaintenanceTask task = taskBuilder().build();
        MaintenanceLog log = logWith(LocalDate.of(2024, 1, 1), 500);

        MaintenanceTaskStatusResult result = calculator.compute(task, 1000, null, List.of(log));

        assertThat(result.status()).isEqualTo(MaintenanceTaskStatus.OK);
        assertThat(result.progress()).isNull();
        assertThat(result.remainingFraction()).isNull();
        assertThat(result.lastServiceMileage()).isEqualTo(500);
        assertThat(result.lastServiceDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void picksLatestLogAmongMultiple() {
        MaintenanceTask task = taskBuilder().oneTime(true).build();
        MaintenanceLog older = logWith(LocalDate.of(2023, 1, 1), 100);
        MaintenanceLog newer = logWith(LocalDate.of(2024, 1, 1), 200);

        MaintenanceTaskStatusResult result = calculator.compute(task, 0, null, List.of(older, newer));

        assertThat(result.lastServiceMileage()).isEqualTo(200);
        assertThat(result.lastServiceDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    private static MaintenanceTask.MaintenanceTaskBuilder taskBuilder() {
        return MaintenanceTask.builder().id(1L).name("Task");
    }

    private static MaintenanceLog logWith(LocalDate performedAt, int mileageAtPerformed) {
        return MaintenanceLog.builder().performedAt(performedAt).mileageAtPerformed(mileageAtPerformed).build();
    }
}
