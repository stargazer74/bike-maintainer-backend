package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ProfileRequest;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.exception.ConflictException;
import de.chriswohlbrecht.maintenance.exception.InvalidProfileNameException;
import de.chriswohlbrecht.maintenance.mapper.UserMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileComponentImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ISessionComponent sessionComponent;

    private ProfileComponentImpl profileComponent;

    @BeforeEach
    void setUp() {
        profileComponent = new ProfileComponentImpl(appUserRepository, vehicleRepository, sessionComponent,
                new UserMapperImpl());
    }

    @Test
    void listProfiles_usesEmailForUsersWithoutDisplayName() {
        when(appUserRepository.findAll(any(Sort.class))).thenReturn(List.of(
                AppUser.builder().id(1L).displayName("Anna").role(UserRole.USER).build(),
                AppUser.builder().id(2L).email("rider@example.org").role(UserRole.USER).build()));

        List<ProfileResponse> result = profileComponent.listProfiles();

        assertThat(result).extracting(ProfileResponse::getDisplayName).containsExactly("Anna", "rider@example.org");
    }

    @Test
    void createProfile_storesTrimmedNameAsUserWithoutEmailAndPassword() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser toSave = invocation.getArgument(0);
            toSave.setId(5L);
            return toSave;
        });

        ProfileResponse result = profileComponent.createProfile(new ProfileRequest().displayName("  Anna "));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Anna");
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getDisplayName()).isEqualTo("Anna");
    }

    @Test
    void createProfile_takenName_throwsConflict() {
        when(appUserRepository.existsByDisplayNameIgnoreCase("Anna")).thenReturn(true);

        assertThatThrownBy(() -> profileComponent.createProfile(new ProfileRequest().displayName("Anna")))
                .isInstanceOf(ConflictException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void createProfile_blankName_isRejected() {
        assertThatThrownBy(() -> profileComponent.createProfile(new ProfileRequest().displayName("   ")))
                .isInstanceOf(InvalidProfileNameException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void updateProfile_renames() {
        AppUser anna = AppUser.builder().id(1L).displayName("Anna").role(UserRole.USER).build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(anna));
        when(appUserRepository.save(anna)).thenReturn(anna);

        Optional<ProfileResponse> result = profileComponent.updateProfile(1L, new ProfileRequest().displayName("Anne"));

        assertThat(result).map(ProfileResponse::getDisplayName).contains("Anne");
    }

    @Test
    void updateProfile_nameTakenByAnotherProfile_throwsConflict() {
        when(appUserRepository.findById(2L))
                .thenReturn(Optional.of(AppUser.builder().id(2L).displayName("Ben").role(UserRole.USER).build()));
        when(appUserRepository.existsByDisplayNameIgnoreCaseAndIdNot("Anna", 2L)).thenReturn(true);

        assertThatThrownBy(() -> profileComponent.updateProfile(2L, new ProfileRequest().displayName("Anna")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateProfile_unknown_returnsEmpty() {
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(profileComponent.updateProfile(9L, new ProfileRequest().displayName("Anna"))).isEmpty();
    }

    @Test
    void deleteProfile_withVehicles_throwsConflict() {
        when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(AppUser.builder().id(1L).displayName("Anna").build()));
        when(vehicleRepository.existsByUser_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> profileComponent.deleteProfile(1L)).isInstanceOf(ConflictException.class);
        verify(appUserRepository, never()).delete(any());
    }

    @Test
    void deleteProfile_withoutVehicles_deletesProfileAndItsSessions() {
        AppUser anna = AppUser.builder().id(1L).displayName("Anna").build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(anna));

        assertThat(profileComponent.deleteProfile(1L)).isTrue();

        verify(appUserRepository).delete(anna);
        verify(sessionComponent).invalidateSessionsOf(1L);
    }

    @Test
    void deleteProfile_unknown_returnsFalse() {
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(profileComponent.deleteProfile(9L)).isFalse();
    }

    @Test
    void selectProfile_startsSessionAsThatUser() {
        AppUser anna = AppUser.builder().id(1L).displayName("Anna").role(UserRole.USER).build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(anna));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        Optional<UserResponse> result = profileComponent.selectProfile(1L, request, response);

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(sessionComponent).startSession(authentication.capture(), eq(request), eq(response));
        assertThat(authentication.getValue().getPrincipal()).isEqualTo(new SessionUser(1L, null));
        assertThat(authentication.getValue().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(result).map(UserResponse::getDisplayName).contains("Anna");
    }

    @Test
    void selectProfile_unknown_returnsEmptyWithoutSession() {
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(profileComponent.selectProfile(9L, mock(HttpServletRequest.class),
                mock(HttpServletResponse.class))).isEmpty();
        verify(sessionComponent, never()).startSession(any(), any(), any());
    }
}
