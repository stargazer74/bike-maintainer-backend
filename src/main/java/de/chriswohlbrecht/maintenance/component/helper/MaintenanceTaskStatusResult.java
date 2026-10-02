package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;

import java.time.LocalDate;

public record MaintenanceTaskStatusResult(
        MaintenanceTaskStatus status,
        Double progress,
        Double remainingFraction,
        Integer kmRemaining,
        Integer daysRemaining,
        Integer lastServiceMileage,
        LocalDate lastServiceDate) {
}
