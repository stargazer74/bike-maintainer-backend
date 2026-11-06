package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.UpdateUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;

import java.util.List;
import java.util.Optional;

/** User administration by admins (portal mode only; the security chain admits only ROLE_ADMIN). */
public interface IAdminUserComponent {

    /** All users, oldest first; never with password hashes. */
    List<UserResponse> listUsers();

    /**
     * Changes role and/or active state. A changed role or a deactivation ends all sessions of the user (the role is
     * part of the session); a deactivation also removes their device logins.
     *
     * @return the updated user, or empty if there is no user with this id
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException no field given, an admin
     *                                                                             deactivating themselves, or the
     *                                                                             last active admin demoted or
     *                                                                             deactivated
     */
    Optional<UserResponse> updateUser(Long userId, UpdateUserRequest request);
}
