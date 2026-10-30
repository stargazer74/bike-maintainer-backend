package de.chriswohlbrecht.maintenance.component.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param totalCost sum of material prices and labor cost; null if the entry has no amounts at all
 * @param currency ISO 4217 code of the amounts of this entry
 */
public record MaintenanceReportEntry(
        LocalDate performedAt,
        Integer mileageAtPerformed,
        String notes,
        List<String> performedTaskNames,
        List<ReportMaterial> materials,
        BigDecimal laborCost,
        BigDecimal totalCost,
        String currency) {

    /** Immutable copy, so the entry cannot be changed through the list passed in or returned. */
    public MaintenanceReportEntry {
        performedTaskNames = List.copyOf(performedTaskNames);
        materials = List.copyOf(materials);
    }

    /** Entry without materials and costs. */
    public MaintenanceReportEntry(LocalDate performedAt, Integer mileageAtPerformed, String notes,
                                  List<String> performedTaskNames) {
        this(performedAt, mileageAtPerformed, notes, performedTaskNames, List.of(), null, null, null);
    }
}
