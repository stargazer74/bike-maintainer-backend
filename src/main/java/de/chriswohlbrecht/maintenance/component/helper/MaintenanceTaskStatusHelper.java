package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MaintenanceTaskStatusHelper {

    private static final int DUE_SOON_THRESHOLD_KM = 1000;
    private static final int DUE_SOON_THRESHOLD_DAYS = 30;
    private static final double DAYS_PER_MONTH = 30.44;

    private final Clock clock;

    public MaintenanceTaskStatusResult compute(
            MaintenanceTask task, int currentMileage, LocalDate vehicleFirstRegistrationDate, List<MaintenanceLog> logsForTask) {
        MaintenanceLog lastLog = findLastLog(logsForTask);
        if (lastLog == null) {
            return computeNeverPerformed(task, currentMileage, vehicleFirstRegistrationDate);
        }

        Integer lastServiceMileage = lastLog.getMileageAtPerformed() != null ? lastLog.getMileageAtPerformed() : 0;
        LocalDate lastServiceDate = lastLog.getPerformedAt();

        if (task.isOneTime()) {
            return new MaintenanceTaskStatusResult(
                    MaintenanceTaskStatus.DONE, null, null, null, null, lastServiceMileage, lastServiceDate);
        }

        AxisResult kmAxis = hasPositive(task.getIntervalKm())
                ? evaluateKmAxis(task.getIntervalKm(), lastServiceMileage, currentMileage)
                : null;
        AxisResult timeAxis = hasPositive(task.getIntervalMonths()) && lastServiceDate != null
                ? evaluateTimeAxis(task.getIntervalMonths(), lastServiceDate)
                : null;

        List<AxisResult> axes = Stream.of(kmAxis, timeAxis).filter(Objects::nonNull).toList();
        if (axes.isEmpty()) {
            return new MaintenanceTaskStatusResult(
                    MaintenanceTaskStatus.OK, null, null, null, null, lastServiceMileage, lastServiceDate);
        }

        return combine(axes, kmAxis, timeAxis, lastServiceMileage, lastServiceDate);
    }

    private MaintenanceTaskStatusResult computeNeverPerformed(
            MaintenanceTask task, int currentMileage, LocalDate vehicleFirstRegistrationDate) {
        Integer firstDueKm = firstPositive(task.getFirstDueKm(), task.getIntervalKm());
        Integer firstDueMonths = firstPositive(task.getFirstDueMonths(), task.getIntervalMonths());

        AxisResult kmAxis = firstDueKm != null ? evaluateKmAxis(firstDueKm, 0, currentMileage) : null;
        AxisResult timeAxis = firstDueMonths != null && vehicleFirstRegistrationDate != null
                ? evaluateTimeAxis(firstDueMonths, vehicleFirstRegistrationDate)
                : null;

        List<AxisResult> axes = Stream.of(kmAxis, timeAxis).filter(Objects::nonNull).toList();
        if (axes.isEmpty()) {
            // No km or month anchor at all — there's nothing to measure against, so flag it
            // rather than silently hide a task that was never performed.
            return new MaintenanceTaskStatusResult(MaintenanceTaskStatus.DUE_SOON, null, 0.0, null, null, null, null);
        }

        return combine(axes, kmAxis, timeAxis, null, null);
    }

    private MaintenanceTaskStatusResult combine(
            List<AxisResult> axes, AxisResult kmAxis, AxisResult timeAxis,
            Integer lastServiceMileage, LocalDate lastServiceDate) {
        AxisResult worst = axes.stream()
                .reduce((a, b) -> severity(b.status()) > severity(a.status()) ? b : a)
                .orElseThrow();
        double progress = axes.stream().mapToDouble(AxisResult::progress).max().orElseThrow();

        return new MaintenanceTaskStatusResult(
                worst.status(),
                progress,
                worst.fraction(),
                worst == kmAxis ? worst.remaining() : null,
                worst == timeAxis ? worst.remaining() : null,
                lastServiceMileage,
                lastServiceDate);
    }

    private AxisResult evaluateKmAxis(int intervalKm, int lastMileage, int currentMileage) {
        int remaining = lastMileage + intervalKm - currentMileage;
        double progress = clamp((double) (currentMileage - lastMileage) / intervalKm);
        double fraction = (double) remaining / intervalKm;
        MaintenanceTaskStatus status = remaining <= 0
                ? MaintenanceTaskStatus.OVERDUE
                : remaining <= DUE_SOON_THRESHOLD_KM ? MaintenanceTaskStatus.DUE_SOON : MaintenanceTaskStatus.OK;
        return new AxisResult(status, progress, fraction, remaining);
    }

    private AxisResult evaluateTimeAxis(int intervalMonths, LocalDate anchorDate) {
        double intervalDays = intervalMonths * DAYS_PER_MONTH;
        long elapsedDays = ChronoUnit.DAYS.between(anchorDate, LocalDate.now(clock));
        double remaining = intervalDays - elapsedDays;
        double progress = clamp(elapsedDays / intervalDays);
        double fraction = remaining / intervalDays;
        MaintenanceTaskStatus status = remaining <= 0
                ? MaintenanceTaskStatus.OVERDUE
                : remaining <= DUE_SOON_THRESHOLD_DAYS ? MaintenanceTaskStatus.DUE_SOON : MaintenanceTaskStatus.OK;
        return new AxisResult(status, progress, fraction, (int) Math.round(remaining));
    }

    private static MaintenanceLog findLastLog(List<MaintenanceLog> logs) {
        return logs.stream()
                .filter(log -> log.getPerformedAt() != null)
                .max(Comparator.comparing(MaintenanceLog::getPerformedAt))
                .orElse(null);
    }

    private static boolean hasPositive(Integer value) {
        return value != null && value > 0;
    }

    private static Integer firstPositive(Integer primary, Integer fallback) {
        if (hasPositive(primary)) {
            return primary;
        }
        return hasPositive(fallback) ? fallback : null;
    }

    private static double clamp(double value) {
        return Math.min(1, Math.max(0, value));
    }

    private static int severity(MaintenanceTaskStatus status) {
        return switch (status) {
            case OK -> 0;
            case DUE_SOON -> 1;
            case OVERDUE -> 2;
            case DONE -> -1;
        };
    }

    private record AxisResult(MaintenanceTaskStatus status, double progress, double fraction, int remaining) {
    }
}
