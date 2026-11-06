package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeleteAccountRequest;

/** Users deleting their own account (portal mode only; household profiles are deleted via the profile endpoints). */
public interface IAccountDeletionComponent {

    /**
     * Permanently deletes the logged-in user with all vehicles, maintenance tasks, logs and costs, open tokens and
     * all other sessions. The caller ends the current session afterwards.
     *
     * @param currentSessionId the session of this request, left to the caller's logout
     * @throws de.chriswohlbrecht.maintenance.exception.TooManyRequestsException account temporarily locked
     * @throws de.chriswohlbrecht.maintenance.exception.WrongPasswordException   password wrong; counts as a failed
     *                                                                           login attempt
     * @throws de.chriswohlbrecht.maintenance.exception.LastAdminException       the user is the last active admin
     */
    void deleteCurrentUser(DeleteAccountRequest request, String currentSessionId);
}
