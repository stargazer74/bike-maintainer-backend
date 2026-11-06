package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.UpdateUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
import de.chriswohlbrecht.maintenance.mapper.UserMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserComponentImplTest {

    private static final long ADMIN_ID = 1L;
    private static final long RIDER_ID = 7L;

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private ISessionComponent sessionComponent;
    @Mock
    private IDeviceLoginComponent deviceLoginComponent;

    private AdminUserComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new AdminUserComponentImpl(appUserRepository, currentUserComponent, sessionComponent,
                deviceLoginComponent, new UserMapperImpl());
    }

    @Test
    void listsAllUsersOldestFirst() {
        when(appUserRepository.findAll(Sort.by("createdAt", "id")))
                .thenReturn(List.of(user(ADMIN_ID, UserRole.ADMIN, true), user(RIDER_ID, UserRole.USER, true)));

        List<UserResponse> users = component.listUsers();

        assertThat(users).extracting(UserResponse::getId).containsExactly(ADMIN_ID, RIDER_ID);
        assertThat(users.get(1).getEmail()).isEqualTo("user7@example.org");
    }

    @Test
    void unknownUserIsEmpty() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(component.updateUser(99L, new UpdateUserRequest().active(false))).isEmpty();
    }

    @Test
    void updateWithoutFieldsIsRejected() {
        assertThatThrownBy(() -> component.updateUser(RIDER_ID, new UpdateUserRequest()))
                .isInstanceOf(InvalidUserUpdateException.class);

        verifyNoInteractions(appUserRepository, sessionComponent);
    }

    @Test
    void deactivatingAUserEndsSessionsAndRevokesDevices() {
        AppUser rider = user(RIDER_ID, UserRole.USER, true);
        when(appUserRepository.findById(RIDER_ID)).thenReturn(Optional.of(rider));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.save(rider)).thenReturn(rider);

        Optional<UserResponse> response = component.updateUser(RIDER_ID, new UpdateUserRequest().active(false));

        assertThat(response).map(UserResponse::getActive).contains(false);
        assertThat(rider.getActive()).isFalse();
        verify(sessionComponent).invalidateSessionsOf(RIDER_ID);
        verify(deviceLoginComponent).revokeAllOf(RIDER_ID);
    }

    @Test
    void changingTheRoleEndsSessionsSoTheNewRoleApplies() {
        AppUser rider = user(RIDER_ID, UserRole.USER, true);
        when(appUserRepository.findById(RIDER_ID)).thenReturn(Optional.of(rider));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.save(rider)).thenReturn(rider);

        component.updateUser(RIDER_ID, new UpdateUserRequest()
                .role(de.chriswohlbrecht.maintenance.api.model.UserRole.ADMIN));

        assertThat(rider.getRole()).isEqualTo(UserRole.ADMIN);
        verify(sessionComponent).invalidateSessionsOf(RIDER_ID);
        verify(deviceLoginComponent, never()).revokeAllOf(anyLong());
    }

    @Test
    void unchangedValuesKeepSessions() {
        AppUser rider = user(RIDER_ID, UserRole.USER, true);
        when(appUserRepository.findById(RIDER_ID)).thenReturn(Optional.of(rider));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.save(rider)).thenReturn(rider);

        component.updateUser(RIDER_ID, new UpdateUserRequest().active(true)
                .role(de.chriswohlbrecht.maintenance.api.model.UserRole.USER));

        verifyNoInteractions(sessionComponent, deviceLoginComponent);
    }

    @Test
    void reactivatingAUserKeepsTheirNewSessionsAlone() {
        AppUser rider = user(RIDER_ID, UserRole.USER, false);
        when(appUserRepository.findById(RIDER_ID)).thenReturn(Optional.of(rider));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.save(rider)).thenReturn(rider);

        component.updateUser(RIDER_ID, new UpdateUserRequest().active(true));

        assertThat(rider.getActive()).isTrue();
        verify(deviceLoginComponent, never()).revokeAllOf(anyLong());
    }

    @Test
    void adminsCannotDeactivateThemselves() {
        AppUser admin = user(ADMIN_ID, UserRole.ADMIN, true);
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);

        assertThatThrownBy(() -> component.updateUser(ADMIN_ID, new UpdateUserRequest().active(false)))
                .isInstanceOf(InvalidUserUpdateException.class);

        assertThat(admin.getActive()).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void lastActiveAdminCannotBeDemoted() {
        AppUser admin = user(ADMIN_ID, UserRole.ADMIN, true);
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> component.updateUser(ADMIN_ID, new UpdateUserRequest()
                .role(de.chriswohlbrecht.maintenance.api.model.UserRole.USER)))
                .isInstanceOf(InvalidUserUpdateException.class);

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void lastActiveAdminCannotBeDeactivatedByAnotherAdmin() {
        AppUser otherAdmin = user(2L, UserRole.ADMIN, true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> component.updateUser(2L, new UpdateUserRequest().active(false)))
                .isInstanceOf(InvalidUserUpdateException.class);
    }

    @Test
    void adminCanBeDemotedWhileAnotherActiveAdminExists() {
        AppUser otherAdmin = user(2L, UserRole.ADMIN, true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(2L);
        when(appUserRepository.save(otherAdmin)).thenReturn(otherAdmin);

        component.updateUser(2L, new UpdateUserRequest().role(de.chriswohlbrecht.maintenance.api.model.UserRole.USER));

        assertThat(otherAdmin.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void inactiveAdminCanBeDemotedEvenIfOnlyOneActiveAdminIsLeft() {
        AppUser inactiveAdmin = user(2L, UserRole.ADMIN, false);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(inactiveAdmin));
        when(currentUserComponent.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(appUserRepository.save(inactiveAdmin)).thenReturn(inactiveAdmin);

        component.updateUser(2L, new UpdateUserRequest().role(de.chriswohlbrecht.maintenance.api.model.UserRole.USER));

        assertThat(inactiveAdmin.getRole()).isEqualTo(UserRole.USER);
        verify(appUserRepository, never()).countByRoleAndActiveTrue(any());
    }

    private static AppUser user(long id, UserRole role, boolean active) {
        return AppUser.builder().id(id).email("user" + id + "@example.org").role(role).active(active)
                .emailVerified(true).build();
    }
}
