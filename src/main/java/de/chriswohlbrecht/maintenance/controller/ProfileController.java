package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.ProfilesApi;
import de.chriswohlbrecht.maintenance.api.model.ProfileRequest;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.IProfileComponent;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/** Household mode only; in portal mode the endpoints are not mapped (and the security chain answers 404). */
@RestController
@RequiredArgsConstructor
@Conditional(OperatingModeCondition.Household.class)
public class ProfileController implements ProfilesApi {

    private final IProfileComponent profileComponent;

    @Override
    public ResponseEntity<List<ProfileResponse>> listProfiles() {
        return ResponseEntity.ok(profileComponent.listProfiles());
    }

    @Override
    public ResponseEntity<ProfileResponse> createProfile(ProfileRequest profileRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileComponent.createProfile(profileRequest));
    }

    @Override
    public ResponseEntity<ProfileResponse> updateProfile(Long profileId, ProfileRequest profileRequest) {
        return profileComponent.updateProfile(profileId, profileRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteProfile(Long profileId) {
        if (!profileComponent.deleteProfile(profileId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> selectProfile(Long profileId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return profileComponent.selectProfile(profileId, attributes.getRequest(), attributes.getResponse())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
