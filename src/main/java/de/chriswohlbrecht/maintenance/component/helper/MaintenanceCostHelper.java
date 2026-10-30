package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class MaintenanceCostHelper {

    /**
     * Sum of the material prices (missing price counts as 0) plus the labor cost; {@code null} if the entry has
     * neither a price nor labor cost, so that "no costs entered" is not shown as 0.
     */
    public BigDecimal totalCost(List<MaintenanceLogMaterial> materials, BigDecimal laborCost) {
        List<BigDecimal> amounts = materials.stream()
                .map(MaintenanceLogMaterial::getPrice)
                .filter(Objects::nonNull)
                .toList();
        if (amounts.isEmpty() && laborCost == null) {
            return null;
        }
        BigDecimal total = laborCost == null ? BigDecimal.ZERO : laborCost;
        for (BigDecimal amount : amounts) {
            total = total.add(amount);
        }
        return total;
    }
}
