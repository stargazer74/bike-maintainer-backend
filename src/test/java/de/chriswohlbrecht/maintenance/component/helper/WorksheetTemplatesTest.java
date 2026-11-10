package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate.BRAKES;
import static de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate.CHAIN;
import static de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate.TIRES;
import static de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate.VALVE_CLEARANCE;
import static org.assertj.core.api.Assertions.assertThat;

class WorksheetTemplatesTest {

    @ParameterizedTest
    @CsvSource({
            "MOTORCYCLE, VALVE_CLEARANCE BRAKES CHAIN TIRES",
            "CAR,        VALVE_CLEARANCE BRAKES TIRES",
            "BICYCLE,    BRAKES CHAIN TIRES",
            "E_BIKE,     BRAKES CHAIN TIRES",
            "QUAD,       VALVE_CLEARANCE BRAKES CHAIN TIRES",
            "SCOOTER,    VALVE_CLEARANCE BRAKES TIRES"
    })
    void availableFor_listsTheFittingTemplatesInFixedOrder(VehicleType type, String expected) {
        assertThat(WorksheetTemplates.availableFor(type))
                .map(Enum::name)
                .containsExactlyElementsOf(Arrays.asList(expected.split(" ")));
    }

    @ParameterizedTest
    @CsvSource({"BICYCLE", "E_BIKE"})
    void valveClearance_isNotOfferedWithoutCombustionEngine(VehicleType type) {
        assertThat(WorksheetTemplates.isAvailable(VALVE_CLEARANCE, type)).isFalse();
        assertThat(WorksheetTemplates.isAvailable(BRAKES, type)).isTrue();
        assertThat(WorksheetTemplates.isAvailable(TIRES, type)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"CAR", "SCOOTER"})
    void chain_isNotOfferedForVehiclesWithoutChain(VehicleType type) {
        assertThat(WorksheetTemplates.isAvailable(CHAIN, type)).isFalse();
    }
}
