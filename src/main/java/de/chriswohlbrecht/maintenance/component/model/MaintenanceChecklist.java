package de.chriswohlbrecht.maintenance.component.model;

/** A generated checklist PDF and the file name to offer it under. */
public record MaintenanceChecklist(String fileName, byte[] content) {

    public MaintenanceChecklist {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
