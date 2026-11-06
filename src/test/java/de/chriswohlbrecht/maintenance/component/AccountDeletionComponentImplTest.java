package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeleteAccountRequest;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.LastAdminException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.exception.WrongPasswordException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogMaterialRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionComponentImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final String SESSION = "current-session";

    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private ISessionComponent sessionComponent;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;
    @Mock
    private MaintenanceLogRepository maintenanceLogRepository;
    @Mock
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    @Mock
    private MaintenanceLogMaterialRepository maintenanceLogMaterialRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AccountDeletionComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new AccountDeletionComponentImpl(currentUserComponent, sessionComponent, appUserRepository,
                vehicleRepository, maintenanceTaskRepository, maintenanceLogRepository, maintenanceLogTaskRepository,
                maintenanceLogMaterialRepository, passwordEncoder,
                new SecurityProperties(10, Duration.ofMinutes(15), 3, Duration.ofMinutes(15)),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), meterRegistry);
    }

    @Test
    void deletesVehiclesWithDependentsThenUserAndOtherSessions() {
        AppUser user = user(UserRole.USER);
        Vehicle first = Vehicle.builder().id(11L).build();
        Vehicle second = Vehicle.builder().id(12L).build();
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("secret-password", "{bcrypt}hash")).thenReturn(true);
        when(vehicleRepository.findAllByUser_Id(7L)).thenReturn(List.of(first, second));

        component.deleteCurrentUser(request("secret-password"), SESSION);

        InOrder order = inOrder(maintenanceLogMaterialRepository, maintenanceLogTaskRepository,
                maintenanceLogRepository, maintenanceTaskRepository, vehicleRepository, appUserRepository,
                sessionComponent);
        for (Vehicle vehicle : List.of(first, second)) {
            order.verify(maintenanceLogMaterialRepository).deleteAllByLog_Vehicle_Id(vehicle.getId());
            order.verify(maintenanceLogTaskRepository).deleteAllByLog_Vehicle_Id(vehicle.getId());
            order.verify(maintenanceLogRepository).deleteAllByVehicle_Id(vehicle.getId());
            order.verify(maintenanceTaskRepository).deleteAllByVehicle_Id(vehicle.getId());
            order.verify(vehicleRepository).delete(vehicle);
        }
        order.verify(appUserRepository).delete(user);
        order.verify(sessionComponent).invalidateOtherSessionsOf(7L, SESSION);
        assertThat(meterRegistry.counter("bm.accounts.deleted").count()).isEqualTo(1.0);
    }

    @Test
    void metricIsRegisteredWithZero() {
        assertThat(meterRegistry.find("bm.accounts.deleted").counter()).isNotNull();
        assertThat(meterRegistry.counter("bm.accounts.deleted").count()).isZero();
    }

    @Test
    void wrongPasswordCountsAsFailedAttemptAndDeletesNothing() {
        AppUser user = user(UserRole.USER);
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> component.deleteCurrentUser(request("wrong"), SESSION))
                .isInstanceOf(WrongPasswordException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
        verify(appUserRepository, never()).delete(any());
        verifyNoInteractions(vehicleRepository, sessionComponent);
    }

    @Test
    void lastAllowedWrongPasswordLocksTheAccount() {
        AppUser user = user(UserRole.USER);
        user.setFailedLoginAttempts(2);
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> component.deleteCurrentUser(request("wrong"), SESSION))
                .isInstanceOf(WrongPasswordException.class);

        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusMinutes(15));
        assertThat(meterRegistry.counter("bm.auth.lockout").count()).isEqualTo(1.0);
    }

    @Test
    void lockedAccountIsRejectedBeforeThePasswordIsChecked() {
        AppUser user = user(UserRole.USER);
        user.setLockedUntil(NOW.plusMinutes(5));
        when(currentUserComponent.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> component.deleteCurrentUser(request("secret-password"), SESSION))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(passwordEncoder, vehicleRepository, sessionComponent);
    }

    @Test
    void expiredLockDoesNotBlock() {
        AppUser user = user(UserRole.USER);
        user.setLockedUntil(NOW.minusMinutes(1));
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("secret-password", "{bcrypt}hash")).thenReturn(true);

        component.deleteCurrentUser(request("secret-password"), SESSION);

        verify(appUserRepository).delete(user);
    }

    @Test
    void lastActiveAdminCannotDeleteTheAccount() {
        AppUser admin = user(UserRole.ADMIN);
        when(currentUserComponent.getCurrentUser()).thenReturn(admin);
        when(passwordEncoder.matches("secret-password", "{bcrypt}hash")).thenReturn(true);
        when(appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> component.deleteCurrentUser(request("secret-password"), SESSION))
                .isInstanceOf(LastAdminException.class);

        verify(appUserRepository, never()).delete(any());
        verifyNoInteractions(vehicleRepository, sessionComponent);
    }

    @Test
    void adminCanDeleteTheAccountWhenAnotherActiveAdminExists() {
        AppUser admin = user(UserRole.ADMIN);
        when(currentUserComponent.getCurrentUser()).thenReturn(admin);
        when(passwordEncoder.matches("secret-password", "{bcrypt}hash")).thenReturn(true);
        when(appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(2L);

        component.deleteCurrentUser(request("secret-password"), SESSION);

        verify(appUserRepository).delete(admin);
    }

    @Test
    void userWithoutVehiclesIsDeleted() {
        AppUser user = user(UserRole.USER);
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("secret-password", "{bcrypt}hash")).thenReturn(true);
        when(vehicleRepository.findAllByUser_Id(anyLong())).thenReturn(List.of());

        component.deleteCurrentUser(request("secret-password"), SESSION);

        verify(appUserRepository).delete(user);
        verifyNoInteractions(maintenanceTaskRepository, maintenanceLogRepository);
    }

    private static DeleteAccountRequest request(String password) {
        return new DeleteAccountRequest().password(password);
    }

    private static AppUser user(UserRole role) {
        return AppUser.builder()
                .id(7L)
                .email("rider@example.org")
                .passwordHash("{bcrypt}hash")
                .role(role)
                .active(true)
                .emailVerified(true)
                .build();
    }
}
