package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AuthApi;
import de.chriswohlbrecht.maintenance.api.model.ChangePasswordRequest;
import de.chriswohlbrecht.maintenance.api.model.DeleteAccountRequest;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginRequest;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceSessionRequest;
import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.PasswordResetConfirmRequest;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.TokenRequest;
import de.chriswohlbrecht.maintenance.api.model.UpdateCurrentUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.DeviceLoginComponentImpl;
import de.chriswohlbrecht.maintenance.component.IAccountDeletionComponent;
import de.chriswohlbrecht.maintenance.component.IAuthComponent;
import de.chriswohlbrecht.maintenance.component.IDeviceLoginComponent;
import de.chriswohlbrecht.maintenance.component.IPasswordComponent;
import de.chriswohlbrecht.maintenance.component.IRegistrationComponent;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Login, logout, current user and its settings (language), registration, email verification, password change,
 * password reset, deleting the own account and device logins of apps.
 *
 * <p>Registration and passwords exist only in portal mode; in household mode their endpoints answer 404 before
 * reaching this controller ({@code HouseholdSecurityConfiguration}), hence the optional dependencies.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final IAuthComponent authComponent;
    private final ObjectProvider<IRegistrationComponent> registrationComponent;
    private final ObjectProvider<IPasswordComponent> passwordComponent;
    private final ObjectProvider<IAccountDeletionComponent> accountDeletionComponent;
    private final ObjectProvider<IDeviceLoginComponent> deviceLoginComponent;
    private final JsonMapper jsonMapper;

    /** The challenge JSON of the library is passed on as is, only parsed into a map for the generated signature. */
    @Override
    public ResponseEntity<Map<String, Object>> getAuthChallenge() {
        String challenge = registrationComponent.getObject().createChallengeJson();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jsonMapper.readValue(challenge, JSON_OBJECT));
    }

    @Override
    public ResponseEntity<Void> register(RegisterRequest registerRequest) {
        registrationComponent.getObject().register(registerRequest,
                servletRequestAttributes().getRequest().getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> verifyEmail(TokenRequest tokenRequest) {
        registrationComponent.getObject().verifyEmail(tokenRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resendVerificationEmail(EmailRequest emailRequest) {
        registrationComponent.getObject().resendVerification(emailRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(EmailRequest emailRequest) {
        passwordComponent.getObject().requestReset(emailRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirmRequest passwordResetConfirmRequest) {
        passwordComponent.getObject().confirmReset(passwordResetConfirmRequest);
        return ResponseEntity.noContent().build();
    }

    /** The session exists, since the endpoint requires a login. */
    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        passwordComponent.getObject().changePassword(changePasswordRequest,
                servletRequestAttributes().getRequest().getSession().getId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> login(LoginRequest loginRequest) {
        ServletRequestAttributes attributes = servletRequestAttributes();
        return ResponseEntity.ok(authComponent.login(loginRequest, attributes.getRequest(),
                attributes.getResponse()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        ServletRequestAttributes attributes = servletRequestAttributes();
        authComponent.logout(attributes.getRequest(), attributes.getResponse());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(authComponent.getCurrentUser());
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateCurrentUserRequest updateCurrentUserRequest) {
        return ResponseEntity.ok(authComponent.updateCurrentUser(updateCurrentUserRequest));
    }

    /** Other sessions are deleted with the account; the current one ends like on logout (cookie and CSRF token). */
    @Override
    public ResponseEntity<Void> deleteCurrentUser(DeleteAccountRequest deleteAccountRequest) {
        ServletRequestAttributes attributes = servletRequestAttributes();
        accountDeletionComponent.getObject().deleteCurrentUser(deleteAccountRequest,
                attributes.getRequest().getSession().getId());
        authComponent.logout(attributes.getRequest(), attributes.getResponse());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DeviceLoginResponse> deviceLogin(DeviceLoginRequest deviceLoginRequest) {
        ServletRequestAttributes attributes = servletRequestAttributes();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(deviceLoginComponent.getObject().login(deviceLoginRequest, attributes.getRequest(),
                        attributes.getResponse()));
    }

    @Override
    public ResponseEntity<DeviceLoginResponse> createDeviceSession(DeviceSessionRequest deviceSessionRequest) {
        ServletRequestAttributes attributes = servletRequestAttributes();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(deviceLoginComponent.getObject().exchange(deviceSessionRequest, attributes.getRequest(),
                        attributes.getResponse()));
    }

    /** The device that started this session (if any) is marked as {@code current}. */
    @Override
    public ResponseEntity<List<DeviceResponse>> listDevices() {
        HttpSession session = servletRequestAttributes().getRequest().getSession(false);
        Long currentDeviceId = session != null
                && session.getAttribute(DeviceLoginComponentImpl.SESSION_ATTRIBUTE) instanceof Long id ? id : null;
        return ResponseEntity.ok(deviceLoginComponent.getObject().listDevices(currentDeviceId));
    }

    @Override
    public ResponseEntity<Void> deleteDevice(Long deviceId) {
        return deviceLoginComponent.getObject().deleteDevice(deviceId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** The generated interface has no servlet parameters; login, logout, password change and deletion need the session. */
    private static ServletRequestAttributes servletRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }
}
