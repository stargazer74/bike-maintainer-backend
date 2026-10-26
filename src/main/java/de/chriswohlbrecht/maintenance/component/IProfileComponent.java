package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ProfileRequest;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Optional;

/** Profiles of household mode: users without email and password, picked by name instead of logging in. */
public interface IProfileComponent {

    List<ProfileResponse> listProfiles();

    /**
     * @throws de.chriswohlbrecht.maintenance.exception.ConflictException           name already taken
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidProfileNameException name blank
     */
    ProfileResponse createProfile(ProfileRequest request);

    /** Empty if the profile does not exist; otherwise as {@link #createProfile}. */
    Optional<ProfileResponse> updateProfile(Long profileId, ProfileRequest request);

    /**
     * Deletes the profile and its sessions; {@code false} if it does not exist.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.ConflictException the profile still has vehicles
     */
    boolean deleteProfile(Long profileId);

    /** Starts a session as this profile, like a login; empty if the profile does not exist. */
    Optional<UserResponse> selectProfile(Long profileId, HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse);
}
