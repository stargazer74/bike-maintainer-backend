package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeviceLoginRequest;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceSessionRequest;
import de.chriswohlbrecht.maintenance.component.helper.SecureTokenHelper;
import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.mapper.UserMapper;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.DeviceLogin;
import de.chriswohlbrecht.maintenance.persistence.repository.DeviceLoginRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Device logins for apps. Only hashes of the tokens are stored; the previous hash detects a copied token. Rejections
 * of a token never say why, so a guessed or stolen token learns nothing.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class DeviceLoginComponentImpl implements IDeviceLoginComponent {

    /** Session attribute with the id of the device that started the session. */
    public static final String SESSION_ATTRIBUTE = "bm.deviceId";

    private static final String LOGIN_METRIC = "bm.auth.device_login";
    private static final String SESSION_METRIC = "bm.auth.device_session";
    private static final String INVALID_TOKEN = "Invalid device token";

    private final AuthenticationManager authenticationManager;
    private final ISessionComponent sessionComponent;
    private final ICurrentUserComponent currentUserComponent;
    private final UserMapper userMapper;
    private final DeviceLoginRepository deviceLoginRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressWarnings("java:S107") // collaborators of login, session start, persistence and monitoring
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public DeviceLoginComponentImpl(AuthenticationManager authenticationManager, ISessionComponent sessionComponent,
                                    ICurrentUserComponent currentUserComponent, UserMapper userMapper,
                                    DeviceLoginRepository deviceLoginRepository,
                                    SecurityProperties securityProperties, Clock clock, MeterRegistry meterRegistry) {
        this.authenticationManager = authenticationManager;
        this.sessionComponent = sessionComponent;
        this.currentUserComponent = currentUserComponent;
        this.userMapper = userMapper;
        this.deviceLoginRepository = deviceLoginRepository;
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        for (String result : List.of("success", "bad_credentials", "locked", "disabled", "unverified")) {
            meterRegistry.counter(LOGIN_METRIC, "result", result);
        }
        for (String result : List.of("success", "invalid", "reused")) {
            meterRegistry.counter(SESSION_METRIC, "result", result);
        }
    }

    /**
     * No surrounding transaction: the login provider saves failed attempts immediately, and a rollback here would
     * undo them (and with them the account lockout).
     */
    @Override
    public DeviceLoginResponse login(DeviceLoginRequest request, HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        Authentication authentication = authenticate(request);
        sessionComponent.startSession(authentication, httpRequest, httpResponse);
        AppUser user = currentUserComponent.getCurrentUser();

        LocalDateTime now = LocalDateTime.now(clock);
        String token = SecureTokenHelper.generate();
        DeviceLogin device = new DeviceLogin();
        device.setUser(user);
        device.setName(request.getDeviceName().trim());
        device.setTokenHash(SecureTokenHelper.hash(token));
        device.setCreatedAt(now);
        device.setLastUsedAt(now);
        DeviceLogin saved = deviceLoginRepository.save(device);

        httpRequest.getSession().setAttribute(SESSION_ATTRIBUTE, saved.getId());
        countLogin("success");
        log.info("User {} registered device {}", user.getId(), saved.getId());
        return new DeviceLoginResponse().user(userMapper.toResponse(user)).deviceToken(token);
    }

    /** A detected reuse deletes the device although the request fails. */
    @Override
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public DeviceLoginResponse exchange(DeviceSessionRequest request, HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        String hash = SecureTokenHelper.hash(request.getDeviceToken());
        Optional<DeviceLogin> found = deviceLoginRepository.findByTokenHash(hash);
        if (found.isEmpty()) {
            Optional<DeviceLogin> reused = deviceLoginRepository.findByPreviousTokenHash(hash);
            if (reused.isPresent()) {
                deviceLoginRepository.delete(reused.get());
                log.warn("Device {} removed: an already exchanged token was used again", reused.get().getId());
                throw rejected("reused");
            }
            throw rejected("invalid");
        }

        DeviceLogin device = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (device.getLastUsedAt().isBefore(now.minus(securityProperties.deviceLoginIdleTimeout()))) {
            deviceLoginRepository.delete(device);
            throw rejected("invalid");
        }
        AppUser user = device.getUser();
        if (!Boolean.TRUE.equals(user.getActive()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw rejected("invalid");
        }

        String token = SecureTokenHelper.generate();
        if (deviceLoginRepository.rotate(device.getId(), hash, SecureTokenHelper.hash(token), now) != 1) {
            // Another request exchanged the same token a moment earlier; that one keeps the device.
            throw rejected("invalid");
        }
        sessionComponent.startSession(SessionUser.authenticationFor(user), httpRequest, httpResponse);
        httpRequest.getSession().setAttribute(SESSION_ATTRIBUTE, device.getId());
        meterRegistry.counter(SESSION_METRIC, "result", "success").increment();
        return new DeviceLoginResponse().user(userMapper.toResponse(user)).deviceToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(Long currentDeviceId) {
        return deviceLoginRepository.findAllByUser_IdOrderByLastUsedAtDesc(currentUserComponent.getCurrentUserId())
                .stream()
                .map(device -> new DeviceResponse()
                        .id(device.getId())
                        .name(device.getName())
                        .createdAt(userMapper.toOffsetDateTime(device.getCreatedAt()))
                        .lastUsedAt(userMapper.toOffsetDateTime(device.getLastUsedAt()))
                        .current(device.getId().equals(currentDeviceId)))
                .toList();
    }

    @Override
    @Transactional
    public boolean deleteDevice(Long deviceId) {
        Long userId = currentUserComponent.getCurrentUserId();
        return deviceLoginRepository.findByIdAndUser_Id(deviceId, userId)
                .map(device -> {
                    deviceLoginRepository.delete(device);
                    sessionComponent.invalidateSessionsOfDevice(userId, deviceId);
                    log.info("User {} removed device {}", userId, deviceId);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void revokeDevice(Long deviceId) {
        deviceLoginRepository.deleteById(deviceId);
    }

    @Override
    @Transactional
    public void revokeAllOf(Long userId) {
        deviceLoginRepository.deleteAllByUser_Id(userId);
    }

    @Override
    @Transactional
    public int deleteUnused() {
        return deviceLoginRepository.deleteUnusedSince(
                LocalDateTime.now(clock).minus(securityProperties.deviceLoginIdleTimeout()));
    }

    private Authentication authenticate(DeviceLoginRequest request) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException e) {
            countLogin("bad_credentials");
            throw new AuthenticationFailedException(e.getMessage());
        } catch (TooManyRequestsException e) {
            countLogin("locked");
            throw e;
        } catch (AccountUnusableException e) {
            countLogin(AccountUnusableException.ACCOUNT_DISABLED.equals(e.getCode()) ? "disabled" : "unverified");
            throw e;
        }
    }

    private AuthenticationFailedException rejected(String result) {
        meterRegistry.counter(SESSION_METRIC, "result", result).increment();
        return new AuthenticationFailedException(INVALID_TOKEN);
    }

    private void countLogin(String result) {
        meterRegistry.counter(LOGIN_METRIC, "result", result).increment();
    }
}
