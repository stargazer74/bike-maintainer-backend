package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MeasurementStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementStatusHelperTest {

    @ParameterizedTest(name = "{0} in {1}–{2} → {3}")
    @CsvSource(nullValues = "-", value = {
            "0.12, 0.10, 0.15, OK",
            "0.10, 0.10, 0.15, OK",
            "0.15, 0.10, 0.15, OK",
            "0.09, 0.10, 0.15, OUT_OF_RANGE",
            "0.16, 0.10, 0.15, OUT_OF_RANGE",
            "2.0,  1.5,  -,    OK",
            "1.4,  1.5,  -,    OUT_OF_RANGE",
            "320,  -,    319,  OUT_OF_RANGE",
            "-,    0.10, 0.15, UNKNOWN",
            "0.12, -,    -,    UNKNOWN"
    })
    void range(BigDecimal value, BigDecimal min, BigDecimal max, MeasurementStatus expected) {
        assertThat(MeasurementStatusHelper.range(value, min, max)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} mm over {1} links, limit {2} over {3} → {4}")
    @CsvSource(nullValues = "-", value = {
            "318.5, 20, 319, 20, OK",
            "319.5, 20, 319, 20, OUT_OF_RANGE",
            "318.5, 21, 319, 20, UNKNOWN",
            "318.5, -,  319, 20, UNKNOWN",
            "318.5, 20, 319, -,  UNKNOWN",
            "318.5, 20, -,   20, UNKNOWN"
    })
    void chainWear(BigDecimal length, Integer links, BigDecimal max, Integer specLinks, MeasurementStatus expected) {
        assertThat(MeasurementStatusHelper.chainWear(length, links, max, specLinks)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} bar for target {1} → {2}")
    @CsvSource(nullValues = "-", value = {
            "2.5, 2.5, OK",
            "2.4, 2.5, OK",
            "2.6, 2.5, OK",
            "2.39, 2.5, OUT_OF_RANGE",
            "2.61, 2.5, OUT_OF_RANGE",
            "-,   2.5, UNKNOWN",
            "2.5, -,   UNKNOWN"
    })
    void pressure(BigDecimal value, BigDecimal target, MeasurementStatus expected) {
        assertThat(MeasurementStatusHelper.pressure(value, target)).isEqualTo(expected);
    }
}
