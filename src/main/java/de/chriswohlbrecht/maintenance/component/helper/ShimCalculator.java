package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.ShimHint;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveAdjustment;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Suggests the new shim for a measured valve clearance (Epic #113). Clearance and shim thickness add up: a thinner
 * shim gives more clearance. The target is the middle of the target range; the suggestion is a size on the grid of
 * the shim step whose resulting clearance lies in the target range and is closest to the middle.
 */
public final class ShimCalculator {

    private static final int SCALE = 3;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private ShimCalculator() {
    }

    /** Suggested shim, the clearance it gives, and a hint; both values are null if nothing can be suggested. */
    public record Result(BigDecimal suggestedShim, BigDecimal resultingClearance, ShimHint hint) {
    }

    /** @param spec target values of the vehicle, may be null */
    public static Result calculate(ValveKind kind, BigDecimal measured, BigDecimal shimOld,
                                   VehicleSpecification spec) {
        if (spec != null && spec.getValveAdjustment() == ValveAdjustment.SCREW) {
            return new Result(null, null, ShimHint.NOT_SHIM_ADJUSTED);
        }
        BigDecimal min = spec == null ? null : kind == ValveKind.INTAKE ? spec.getIntakeClearanceMin()
                : spec.getExhaustClearanceMin();
        BigDecimal max = spec == null ? null : kind == ValveKind.INTAKE ? spec.getIntakeClearanceMax()
                : spec.getExhaustClearanceMax();
        BigDecimal step = spec == null ? null : spec.getShimStep();
        if (measured == null || shimOld == null || min == null || max == null || step == null
                || step.signum() <= 0) {
            return new Result(null, null, ShimHint.MISSING_INPUT);
        }
        if (measured.compareTo(min) >= 0 && measured.compareTo(max) <= 0) {
            return new Result(scaled(shimOld), scaled(measured), ShimHint.NO_CHANGE_NEEDED);
        }
        // Clearance and shim add up to a constant: clearance(new) = total − new shim.
        BigDecimal total = measured.add(shimOld);
        BigDecimal target = min.add(max).divide(TWO, SCALE + 1, RoundingMode.HALF_UP);
        // New shims that keep the clearance in range: total − max <= shim <= total − min, on the grid of the step.
        BigDecimal lowest = total.subtract(max).divide(step, 0, RoundingMode.CEILING);
        BigDecimal highest = total.subtract(min).divide(step, 0, RoundingMode.FLOOR);
        BigDecimal shim;
        ShimHint hint = null;
        if (lowest.compareTo(highest) <= 0) {
            shim = closestToTarget(lowest.intValueExact(), highest.intValueExact(), step, total, target);
        } else {
            shim = total.subtract(target).divide(step, 0, RoundingMode.HALF_UP).multiply(step);
            hint = ShimHint.NO_SHIM_IN_RANGE;
        }
        if (hint == null && !available(shim, spec)) {
            hint = ShimHint.OUTSIDE_AVAILABLE_SIZES;
        }
        return new Result(scaled(shim), scaled(total.subtract(shim)), hint);
    }

    /** The grid size between {@code lowest} and {@code highest} steps closest to the target; the thicker one on a tie. */
    private static BigDecimal closestToTarget(int lowest, int highest, BigDecimal step, BigDecimal total,
                                              BigDecimal target) {
        BigDecimal best = null;
        BigDecimal bestDistance = null;
        for (int multiple = lowest; multiple <= highest; multiple++) {
            BigDecimal shim = step.multiply(BigDecimal.valueOf(multiple));
            BigDecimal distance = total.subtract(shim).subtract(target).abs();
            if (bestDistance == null || distance.compareTo(bestDistance) <= 0) {
                best = shim;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean available(BigDecimal shim, VehicleSpecification spec) {
        return shim.signum() > 0
                && (spec.getShimMin() == null || shim.compareTo(spec.getShimMin()) >= 0)
                && (spec.getShimMax() == null || shim.compareTo(spec.getShimMax()) <= 0);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
