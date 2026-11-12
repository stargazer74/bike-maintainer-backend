package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

/**
 * Access status of accounts (trial, subscription, read-only). One implementation per operating mode: portal mode
 * evaluates trial and subscription, household mode never restricts anything.
 */
public interface IAccessComponent {

    Access accessOf(AppUser user);

    /**
     * Guards every operation that writes vehicle data (and the worksheet download). Called first by the components,
     * before ownership is checked, so a read-only account learns nothing about foreign IDs. Checked once per request:
     * an import that creates many entries asks the database only once.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.SubscriptionRequiredException if the account of the logged-in
     *                                                                                user is read-only
     */
    void requireWriteAccess();
}
