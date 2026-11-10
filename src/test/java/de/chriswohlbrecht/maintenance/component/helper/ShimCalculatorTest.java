package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.ShimHint;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveAdjustment;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShimCalculatorTest {

    /** Intake 0.10–0.15 mm, exhaust 0.20–0.25 mm, shims from 1.200 to 3.500 mm. */
    private static VehicleSpecification spec(String step) {
        return VehicleSpecification.builder()
                .valveAdjustment(ValveAdjustment.SHIM)
                .intakeClearanceMin(new BigDecimal("0.10")).intakeClearanceMax(new BigDecimal("0.15"))
                .exhaustClearanceMin(new BigDecimal("0.20")).exhaustClearanceMax(new BigDecimal("0.25"))
                .shimStep(step == null ? null : new BigDecimal(step))
                .shimMin(new BigDecimal("1.200")).shimMax(new BigDecimal("3.500"))
                .build();
    }

    @ParameterizedTest(name = "{0} {1} mm with shim {2}, step {3} → {4} gives {5} ({6})")
    @CsvSource(nullValues = "-", value = {
            // too tight: thinner shim; 1.750 gives 0.130 (closest to 0.125), 1.775 only 0.105
            "INTAKE,  0.08,  1.800, 0.025, 1.750, 0.130, -",
            // too loose: thicker shim; 1.925 gives 0.130, 1.950 gives 0.105
            "INTAKE,  0.20,  1.850, 0.025, 1.925, 0.125, -",
            // coarser step: 1.750 gives 0.130 (closest to the middle)
            "INTAKE,  0.08,  1.800, 0.05,  1.750, 0.130, -",
            // exhaust range: total 2.150, 1.925 gives 0.225 exactly in the middle
            "EXHAUST, 0.15,  2.000, 0.025, 1.925, 0.225, -",
            // tie: 1.750 gives 0.150, 1.800 gives 0.100, both 0.025 from the middle; the thicker shim wins
            "INTAKE,  0.08,  1.820, 0.05,  1.800, 0.100, -",
            // already in range: keep the shim
            "INTAKE,  0.12,  1.800, 0.025, 1.800, 0.120, NO_CHANGE_NEEDED",
            "INTAKE,  0.10,  1.800, 0.025, 1.800, 0.100, NO_CHANGE_NEEDED",
            "INTAKE,  0.15,  1.800, 0.025, 1.800, 0.150, NO_CHANGE_NEEDED",
            // step coarser than the range: no grid size in range, the one closest to the ideal (1.755 → 1.8)
            "INTAKE,  0.08,  1.800, 0.3,   1.800, 0.080, NO_SHIM_IN_RANGE",
            // below the smallest available shim
            "INTAKE,  0.02,  1.250, 0.025, 1.150, 0.120, OUTSIDE_AVAILABLE_SIZES",
            // above the largest available shim
            "INTAKE,  0.40,  3.300, 0.025, 3.575, 0.125, OUTSIDE_AVAILABLE_SIZES",
            // missing input
            "INTAKE,  -,     1.800, 0.025, -,     -,     MISSING_INPUT",
            "INTAKE,  0.08,  -,     0.025, -,     -,     MISSING_INPUT",
            "INTAKE,  0.08,  1.800, -,     -,     -,     MISSING_INPUT"
    })
    void calculate(ValveKind kind, BigDecimal measured, BigDecimal shimOld, String step, BigDecimal shim,
                   BigDecimal clearance, ShimHint hint) {
        ShimCalculator.Result result = ShimCalculator.calculate(kind, measured, shimOld, spec(step));

        assertThat(result.hint()).isEqualTo(hint);
        if (shim == null) {
            assertThat(result.suggestedShim()).isNull();
            assertThat(result.resultingClearance()).isNull();
        } else {
            assertThat(result.suggestedShim()).isEqualByComparingTo(shim);
            assertThat(result.resultingClearance()).isEqualByComparingTo(clearance);
            assertThat(result.suggestedShim().scale()).isEqualTo(3);
        }
    }

    @Test
    void screwAdjustedEngine_getsNoSuggestion() {
        VehicleSpecification spec = spec("0.025");
        spec.setValveAdjustment(ValveAdjustment.SCREW);

        ShimCalculator.Result result = ShimCalculator.calculate(ValveKind.INTAKE, new BigDecimal("0.08"),
                new BigDecimal("1.800"), spec);

        assertThat(result).isEqualTo(new ShimCalculator.Result(null, null, ShimHint.NOT_SHIM_ADJUSTED));
    }

    @Test
    void withoutTargetValues_missingInput() {
        assertThat(ShimCalculator.calculate(ValveKind.EXHAUST, new BigDecimal("0.15"), new BigDecimal("2.000"),
                null).hint()).isEqualTo(ShimHint.MISSING_INPUT);
        VehicleSpecification onlyMin = spec("0.025");
        onlyMin.setExhaustClearanceMax(null);
        assertThat(ShimCalculator.calculate(ValveKind.EXHAUST, new BigDecimal("0.15"), new BigDecimal("2.000"),
                onlyMin).hint()).isEqualTo(ShimHint.MISSING_INPUT);
    }

    @Test
    void withoutAvailableSizes_everyPositiveGridSizeIsFine() {
        VehicleSpecification spec = spec("0.025");
        spec.setShimMin(null);
        spec.setShimMax(null);

        ShimCalculator.Result result = ShimCalculator.calculate(ValveKind.INTAKE, new BigDecimal("0.02"),
                new BigDecimal("1.250"), spec);

        assertThat(result.hint()).isNull();
        assertThat(result.suggestedShim()).isEqualByComparingTo("1.150");
    }

    @Test
    void zeroOrNegativeShim_isOutsideTheAvailableSizes() {
        VehicleSpecification spec = spec("0.025");
        spec.setShimMin(null);

        ShimCalculator.Result result = ShimCalculator.calculate(ValveKind.INTAKE, new BigDecimal("0.01"),
                new BigDecimal("0.050"), spec);

        assertThat(result.hint()).isEqualTo(ShimHint.OUTSIDE_AVAILABLE_SIZES);
    }
}
