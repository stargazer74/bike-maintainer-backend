package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceCostHelperTest {

    private final MaintenanceCostHelper helper = new MaintenanceCostHelper();

    private static MaintenanceLogMaterial priced(String price) {
        return MaintenanceLogMaterial.builder().price(price == null ? null : new BigDecimal(price)).build();
    }

    @Test
    void totalCost_isNullWithoutAmounts() {
        assertThat(helper.totalCost(List.of(), null)).isNull();
        assertThat(helper.totalCost(List.of(priced(null)), null)).isNull();
    }

    @Test
    void totalCost_sumsPricesAndLabor() {
        assertThat(helper.totalCost(List.of(priced("12.50"), priced("7.25"), priced(null)), new BigDecimal("30")))
                .isEqualByComparingTo("49.75");
    }

    @Test
    void totalCost_laborOnlyOrPricesOnly() {
        assertThat(helper.totalCost(List.of(), new BigDecimal("0"))).isEqualByComparingTo("0");
        assertThat(helper.totalCost(List.of(priced("5.00")), null)).isEqualByComparingTo("5");
    }
}
