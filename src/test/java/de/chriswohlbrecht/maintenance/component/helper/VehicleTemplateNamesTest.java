package de.chriswohlbrecht.maintenance.component.helper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleTemplateNamesTest {

    @Test
    void ignoresCaseSeparatorsAndPunctuation() {
        assertThat(VehicleTemplateNames.normalize("MOTO-GUZZI")).isEqualTo("motoguzzi");
        assertThat(VehicleTemplateNames.normalize("moto guzzi")).isEqualTo("motoguzzi");
        assertThat(VehicleTemplateNames.normalize("Moto_Guzzi.")).isEqualTo("motoguzzi");
        assertThat(VehicleTemplateNames.normalize("CB 650 R")).isEqualTo("cb650r");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(VehicleTemplateNames.normalize(null)).isEmpty();
    }
}
