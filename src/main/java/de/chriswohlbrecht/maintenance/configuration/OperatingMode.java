package de.chriswohlbrecht.maintenance.configuration;

/** How the service is operated; set via {@code bm.mode} (environment variable {@code BM_MODE}). */
public enum OperatingMode {
    /** Public internet operation with accounts (email + password). The default. */
    PORTAL,
    /** Private operation in a trusted home network: profile selection without passwords. */
    HOUSEHOLD
}
