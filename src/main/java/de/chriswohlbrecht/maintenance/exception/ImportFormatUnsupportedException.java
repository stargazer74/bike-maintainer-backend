package de.chriswohlbrecht.maintenance.exception;

/** The {@code formatVersion} of an import file is unknown, e.g. a newer instance wrote it (400 {@code IMPORT_FORMAT_UNSUPPORTED}). */
public class ImportFormatUnsupportedException extends RuntimeException {

    public static final String CODE = "IMPORT_FORMAT_UNSUPPORTED";

    /** The only version this instance reads and writes. */
    public static final int SUPPORTED_VERSION = 1;

    public ImportFormatUnsupportedException(Integer version) {
        super("Unsupported formatVersion " + version + ", supported: " + SUPPORTED_VERSION);
    }
}
