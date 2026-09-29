package de.chriswohlbrecht.maintenance.component.helper;

import java.time.LocalDate;
import java.util.List;

public record MaintenanceReportEntry(
        LocalDate performedAt,
        Integer mileageAtPerformed,
        String notes,
        List<String> performedTaskNames) {
}
