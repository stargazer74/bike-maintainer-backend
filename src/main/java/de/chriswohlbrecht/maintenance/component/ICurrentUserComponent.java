package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

public interface ICurrentUserComponent {

    /**
     * Returns the user on whose behalf the current request is executed.
     *
     * @throws IllegalStateException if no user can be determined
     */
    AppUser getCurrentUser();
}
