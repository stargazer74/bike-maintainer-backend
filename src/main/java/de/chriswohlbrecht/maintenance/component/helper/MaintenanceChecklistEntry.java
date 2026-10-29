package de.chriswohlbrecht.maintenance.component.helper;

/** One task on the maintenance checklist, with its current due status. */
public record MaintenanceChecklistEntry(
        String name,
        String description,
        boolean oneTime,
        Integer intervalKm,
        Integer intervalMonths,
        MaintenanceTaskStatusResult status) {
}
