package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

public interface ICurrentUserComponent {

    /**
     * Returns the logged-in user on whose behalf the current request is executed.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException if nobody is logged in
     */
    AppUser getCurrentUser();
}
