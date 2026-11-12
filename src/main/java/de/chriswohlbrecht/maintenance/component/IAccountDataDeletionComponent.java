package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

/** The one place that removes an account with everything it owns (portal mode). */
public interface IAccountDataDeletionComponent {

    /**
     * Ends the subscriptions at the payment provider, then deletes the vehicles with maintenance tasks, logs and
     * costs, and the user with tokens, devices and subscriptions. Sessions are left to the caller.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException provider not reachable;
     *                                                                                     nothing is deleted
     */
    void deleteWithAllData(AppUser user);
}
