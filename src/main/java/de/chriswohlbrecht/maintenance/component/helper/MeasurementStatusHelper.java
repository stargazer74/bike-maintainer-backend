package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MeasurementStatus;

import java.math.BigDecimal;
import java.util.Objects;

/** Compares a measured value with a target value of the vehicle (Epic #113). Null-safe: missing means UNKNOWN. */
public final class MeasurementStatusHelper {

    /** Tyre pressure counts as OK up to this deviation from the target. */
    static final BigDecimal PRESSURE_TOLERANCE = new BigDecimal("0.1");

    private MeasurementStatusHelper() {
    }

    /** OUT_OF_RANGE below {@code min} or above {@code max}; a missing bound is not checked. */
    public static MeasurementStatus range(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || (min == null && max == null)) {
            return MeasurementStatus.UNKNOWN;
        }
        boolean tooLow = min != null && value.compareTo(min) < 0;
        boolean tooHigh = max != null && value.compareTo(max) > 0;
        return tooLow || tooHigh ? MeasurementStatus.OUT_OF_RANGE : MeasurementStatus.OK;
    }

    /** Wear limit for the length over a number of links; only comparable over the same number of links. */
    public static MeasurementStatus chainWear(BigDecimal length, Integer links, BigDecimal maxLength,
                                              Integer specLinks) {
        if (links == null || !Objects.equals(links, specLinks)) {
            return MeasurementStatus.UNKNOWN;
        }
        return range(length, null, maxLength);
    }

    /** OK within {@link #PRESSURE_TOLERANCE} of the target. */
    public static MeasurementStatus pressure(BigDecimal value, BigDecimal target) {
        if (target == null) {
            return MeasurementStatus.UNKNOWN;
        }
        return range(value, target.subtract(PRESSURE_TOLERANCE), target.add(PRESSURE_TOLERANCE));
    }
}
