package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ProfileRequest;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.exception.ConflictException;
import de.chriswohlbrecht.maintenance.exception.InvalidProfileNameException;
import de.chriswohlbrecht.maintenance.mapper.UserMapper;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProfileComponentImpl implements IProfileComponent {

    private final AppUserRepository appUserRepository;
    private final VehicleRepository vehicleRepository;
    private final ISessionComponent sessionComponent;
    private final UserMapper userMapper;

    @Override
    public List<ProfileResponse> listProfiles() {
        return appUserRepository.findAll(Sort.by("displayName", "email")).stream()
                .map(userMapper::toProfileResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProfileResponse createProfile(ProfileRequest request) {
        String displayName = normalize(request.getDisplayName());
        if (appUserRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("Profile name is already taken");
        }
        AppUser profile = AppUser.builder()
                .displayName(displayName)
                .role(UserRole.USER)
                .build();
        return userMapper.toProfileResponse(appUserRepository.save(profile));
    }

    @Override
    @Transactional
    public Optional<ProfileResponse> updateProfile(Long profileId, ProfileRequest request) {
        String displayName = normalize(request.getDisplayName());
        return appUserRepository.findById(profileId).map(profile -> {
            if (appUserRepository.existsByDisplayNameIgnoreCaseAndIdNot(displayName, profileId)) {
                throw new ConflictException("Profile name is already taken");
            }
            profile.setDisplayName(displayName);
            return userMapper.toProfileResponse(appUserRepository.save(profile));
        });
    }

    @Override
    @Transactional
    public boolean deleteProfile(Long profileId) {
        Optional<AppUser> profile = appUserRepository.findById(profileId);
        if (profile.isEmpty()) {
            return false;
        }
        // Deleting vehicles implicitly would lose data unnoticed; the user has to delete them first.
        if (vehicleRepository.existsByUser_Id(profileId)) {
            throw new ConflictException("Profile still has vehicles");
        }
        appUserRepository.delete(profile.get());
        sessionComponent.invalidateSessionsOf(profileId);
        return true;
    }

    @Override
    public Optional<UserResponse> selectProfile(Long profileId, HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        return appUserRepository.findById(profileId).map(profile -> {
            sessionComponent.startSession(SessionUser.authenticationFor(profile), httpRequest, httpResponse);
            return userMapper.toResponse(profile);
        });
    }

    private static String normalize(String displayName) {
        String trimmed = displayName == null ? "" : displayName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidProfileNameException("Profile name must not be blank");
        }
        return trimmed;
    }
}
