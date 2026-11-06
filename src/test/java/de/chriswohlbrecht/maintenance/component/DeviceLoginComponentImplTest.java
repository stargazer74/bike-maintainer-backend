package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeviceLoginRequest;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceSessionRequest;
import de.chriswohlbrecht.maintenance.component.helper.SecureTokenHelper;
import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.mapper.UserMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.DeviceLogin;
import de.chriswohlbrecht.maintenance.persistence.repository.DeviceLoginRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceLoginComponentImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final String TOKEN = "t".repeat(43);

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private ISessionComponent sessionComponent;
    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private DeviceLoginRepository deviceLoginRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    private final MockHttpServletResponse httpResponse = new MockHttpServletResponse();
    private DeviceLoginComponentImpl component;
    private AppUser user;

    @BeforeEach
    void setUp() {
        component = new DeviceLoginComponentImpl(authenticationManager, sessionComponent, currentUserComponent,
                new UserMapperImpl(), deviceLoginRepository,
                new SecurityProperties(10, Duration.ofMinutes(15), 10, Duration.ofMinutes(15), Duration.ofDays(90)),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), meterRegistry);
        user = AppUser.builder().id(7L).email("rider@example.org").role(UserRole.USER).active(true)
                .emailVerified(true).build();
    }

    @Test
    void countersAreRegisteredWithZero() {
        for (String result : new String[]{"success", "bad_credentials", "locked", "disabled", "unverified"}) {
            assertThat(meterRegistry.counter("bm.auth.device_login", "result", result).count()).isZero();
        }
        for (String result : new String[]{"success", "invalid", "reused"}) {
            assertThat(meterRegistry.find("bm.auth.device_session").tag("result", result).counter()).isNotNull();
        }
    }

    @Test
    void loginRegistersDeviceStartsSessionAndReturnsToken() {
        when(authenticationManager.authenticate(any())).thenReturn(SessionUser.authenticationFor(user));
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(deviceLoginRepository.save(any())).thenAnswer(invocation -> {
            DeviceLogin device = invocation.getArgument(0);
            device.setId(3L);
            return device;
        });

        DeviceLoginResponse response = component.login(loginRequest("  Pixel 8  "), httpRequest, httpResponse);

        ArgumentCaptor<DeviceLogin> saved = ArgumentCaptor.forClass(DeviceLogin.class);
        verify(deviceLoginRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Pixel 8");
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getLastUsedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokenHelper.hash(response.getDeviceToken()));
        assertThat(response.getDeviceToken()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(response.getUser().getEmail()).isEqualTo("rider@example.org");
        verify(sessionComponent).startSession(any(), eq(httpRequest), eq(httpResponse));
        assertThat(httpRequest.getSession().getAttribute(DeviceLoginComponentImpl.SESSION_ATTRIBUTE)).isEqualTo(3L);
        assertThat(meterRegistry.counter("bm.auth.device_login", "result", "success").count()).isEqualTo(1.0);
    }

    @Test
    void wrongCredentialsAreReportedLikeLogin() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid email or password"));

        assertThatThrownBy(() -> component.login(loginRequest("Pixel 8"), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(deviceLoginRepository, sessionComponent);
        assertThat(meterRegistry.counter("bm.auth.device_login", "result", "bad_credentials").count()).isEqualTo(1.0);
    }

    @Test
    void lockedAccountIsReportedLikeLogin() {
        when(authenticationManager.authenticate(any())).thenThrow(new TooManyRequestsException(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> component.login(loginRequest("Pixel 8"), httpRequest, httpResponse))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(deviceLoginRepository);
        assertThat(meterRegistry.counter("bm.auth.device_login", "result", "locked").count()).isEqualTo(1.0);
    }

    @Test
    void disabledAndUnverifiedAccountsAreReportedLikeLogin() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new AccountUnusableException(AccountUnusableException.ACCOUNT_DISABLED, "disabled"))
                .thenThrow(new AccountUnusableException(AccountUnusableException.EMAIL_NOT_VERIFIED, "unverified"));

        assertThatThrownBy(() -> component.login(loginRequest("Pixel 8"), httpRequest, httpResponse))
                .isInstanceOf(AccountUnusableException.class);
        assertThatThrownBy(() -> component.login(loginRequest("Pixel 8"), httpRequest, httpResponse))
                .isInstanceOf(AccountUnusableException.class);

        verifyNoInteractions(deviceLoginRepository);
        assertThat(meterRegistry.counter("bm.auth.device_login", "result", "disabled").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("bm.auth.device_login", "result", "unverified").count()).isEqualTo(1.0);
    }

    @Test
    void exchangeRotatesTokenAndStartsSession() {
        DeviceLogin device = device(NOW.minusDays(3));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));
        when(deviceLoginRepository.rotate(eq(3L), eq(SecureTokenHelper.hash(TOKEN)), anyString(), eq(NOW)))
                .thenReturn(1);

        DeviceLoginResponse response = component.exchange(sessionRequest(), httpRequest, httpResponse);

        assertThat(response.getDeviceToken()).isNotEqualTo(TOKEN).matches("^[A-Za-z0-9_-]{43}$");
        verify(deviceLoginRepository).rotate(3L, SecureTokenHelper.hash(TOKEN),
                SecureTokenHelper.hash(response.getDeviceToken()), NOW);
        assertThat(response.getUser().getId()).isEqualTo(7L);
        verify(sessionComponent).startSession(any(), eq(httpRequest), eq(httpResponse));
        assertThat(httpRequest.getSession().getAttribute(DeviceLoginComponentImpl.SESSION_ATTRIBUTE)).isEqualTo(3L);
        assertThat(meterRegistry.counter("bm.auth.device_session", "result", "success").count()).isEqualTo(1.0);
    }

    @Test
    void lostRaceIsRejectedWithoutRevokingTheDevice() {
        DeviceLogin device = device(NOW.minusDays(3));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));
        when(deviceLoginRepository.rotate(eq(3L), anyString(), anyString(), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(deviceLoginRepository, never()).delete(any());
        verifyNoInteractions(sessionComponent);
        assertThat(meterRegistry.counter("bm.auth.device_session", "result", "invalid").count()).isEqualTo(1.0);
    }

    @Test
    void reusedPreviousTokenRevokesTheDevice() {
        DeviceLogin device = device(NOW.minusDays(3));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.empty());
        when(deviceLoginRepository.findByPreviousTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(deviceLoginRepository).delete(device);
        verifyNoInteractions(sessionComponent);
        assertThat(meterRegistry.counter("bm.auth.device_session", "result", "reused").count()).isEqualTo(1.0);
    }

    @Test
    void unknownTokenIsRejected() {
        when(deviceLoginRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(deviceLoginRepository.findByPreviousTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verifyNoInteractions(sessionComponent);
        assertThat(meterRegistry.counter("bm.auth.device_session", "result", "invalid").count()).isEqualTo(1.0);
    }

    @Test
    void deviceUnusedFor90DaysIsDeletedAndRejected() {
        DeviceLogin device = device(NOW.minusDays(90).minusMinutes(1));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(deviceLoginRepository).delete(device);
        verifyNoInteractions(sessionComponent);
    }

    @Test
    void deviceUsedJustWithin90DaysIsAccepted() {
        DeviceLogin device = device(NOW.minusDays(90).plusMinutes(1));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));
        when(deviceLoginRepository.rotate(eq(3L), anyString(), anyString(), eq(NOW))).thenReturn(1);

        component.exchange(sessionRequest(), httpRequest, httpResponse);

        verify(sessionComponent).startSession(any(), eq(httpRequest), eq(httpResponse));
    }

    @Test
    void inactiveOrUnverifiedAccountIsRejectedButDeviceKept() {
        DeviceLogin device = device(NOW.minusDays(1));
        when(deviceLoginRepository.findByTokenHash(SecureTokenHelper.hash(TOKEN))).thenReturn(Optional.of(device));

        user.setActive(false);
        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);
        user.setActive(true);
        user.setEmailVerified(false);
        assertThatThrownBy(() -> component.exchange(sessionRequest(), httpRequest, httpResponse))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(deviceLoginRepository, never()).delete(any());
        verify(deviceLoginRepository, never()).rotate(any(), any(), any(), any());
        verifyNoInteractions(sessionComponent);
    }

    @Test
    void listMarksTheDeviceOfTheCurrentSession() {
        DeviceLogin phone = device(NOW);
        DeviceLogin tablet = device(NOW.minusDays(2));
        tablet.setId(4L);
        tablet.setName("Tablet");
        when(currentUserComponent.getCurrentUserId()).thenReturn(7L);
        when(deviceLoginRepository.findAllByUser_IdOrderByLastUsedAtDesc(7L)).thenReturn(List.of(phone, tablet));

        List<DeviceResponse> devices = component.listDevices(4L);

        assertThat(devices).extracting(DeviceResponse::getId).containsExactly(3L, 4L);
        assertThat(devices).extracting(DeviceResponse::getCurrent).containsExactly(false, true);
        assertThat(devices.getFirst().getName()).isEqualTo("Pixel 8");
        assertThat(devices.getFirst().getLastUsedAt()).isNotNull();
    }

    @Test
    void listWithoutDeviceSessionMarksNothing() {
        when(currentUserComponent.getCurrentUserId()).thenReturn(7L);
        when(deviceLoginRepository.findAllByUser_IdOrderByLastUsedAtDesc(7L)).thenReturn(List.of(device(NOW)));

        assertThat(component.listDevices(null)).extracting(DeviceResponse::getCurrent).containsExactly(false);
    }

    @Test
    void deletingOwnDeviceEndsItsSessions() {
        DeviceLogin device = device(NOW);
        when(currentUserComponent.getCurrentUserId()).thenReturn(7L);
        when(deviceLoginRepository.findByIdAndUser_Id(3L, 7L)).thenReturn(Optional.of(device));

        assertThat(component.deleteDevice(3L)).isTrue();

        verify(deviceLoginRepository).delete(device);
        verify(sessionComponent).invalidateSessionsOfDevice(7L, 3L);
    }

    @Test
    void foreignOrUnknownDeviceIsNotDeleted() {
        when(currentUserComponent.getCurrentUserId()).thenReturn(7L);
        when(deviceLoginRepository.findByIdAndUser_Id(9L, 7L)).thenReturn(Optional.empty());

        assertThat(component.deleteDevice(9L)).isFalse();

        verify(deviceLoginRepository, never()).delete(any());
        verifyNoInteractions(sessionComponent);
    }

    @Test
    void revokeDeviceDeletesItById() {
        component.revokeDevice(3L);

        verify(deviceLoginRepository).deleteById(3L);
    }

    @Test
    void revokeAllDeletesEveryDeviceOfTheUser() {
        component.revokeAllOf(7L);

        verify(deviceLoginRepository).deleteAllByUser_Id(7L);
    }

    @Test
    void deleteUnusedUsesTheIdleTimeout() {
        when(deviceLoginRepository.deleteUnusedSince(NOW.minusDays(90))).thenReturn(2);

        assertThat(component.deleteUnused()).isEqualTo(2);
    }

    private DeviceLogin device(LocalDateTime lastUsedAt) {
        DeviceLogin device = new DeviceLogin();
        device.setId(3L);
        device.setUser(user);
        device.setName("Pixel 8");
        device.setTokenHash(SecureTokenHelper.hash(TOKEN));
        device.setCreatedAt(NOW.minusDays(30));
        device.setLastUsedAt(lastUsedAt);
        return device;
    }

    private static DeviceLoginRequest loginRequest(String deviceName) {
        return new DeviceLoginRequest().email("rider@example.org").password("secret-password").deviceName(deviceName);
    }

    private static DeviceSessionRequest sessionRequest() {
        return new DeviceSessionRequest().deviceToken(TOKEN);
    }
}
