package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

import java.util.function.Predicate;

/** The one place that removes an account with everything it owns (portal mode). */
public interface IAccountDataDeletionComponent {

    /**
     * Ends the subscriptions at the payment provider, then deletes the vehicles with maintenance tasks, logs and
     * costs, and the user with tokens, devices and subscriptions. Sessions are left to the caller. Must not be called
     * inside a transaction: the calls to the provider happen before the deletion's own one.
     *
     * @param stillDue checked on the freshly read, locked account right before deleting (e.g. no login since the
     *                 decision); false keeps the account
     * @return whether the account was deleted
     * @throws de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException provider not reachable;
     *                                                                                     nothing is deleted
     */
    boolean deleteWithAllData(AppUser user, Predicate<AppUser> stillDue);

    /** {@link #deleteWithAllData(AppUser, Predicate)} without a condition (e.g. the person deletes their own account). */
    default boolean deleteWithAllData(AppUser user) {
        return deleteWithAllData(user, current -> true);
    }
}
