package de.chriswohlbrecht.maintenance.component;

public interface IInitialAdminComponent {

    /**
     * Sets the configured initial password on the initial admin account if that account has no password yet.
     * An existing password is never overwritten.
     *
     * @throws IllegalStateException if the configured password violates the password rules
     */
    void initializePassword();
}
