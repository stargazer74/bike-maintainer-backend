package de.chriswohlbrecht.maintenance.component.model;

/** A generated worksheet PDF and the file name to offer it under. */
public record Worksheet(String fileName, byte[] content) {

    public Worksheet {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
