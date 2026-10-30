package de.chriswohlbrecht.maintenance.component.model;

/** A generated maintenance report PDF and the file name to offer it under. */
public record MaintenanceReport(String fileName, byte[] content) {

    public MaintenanceReport {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
