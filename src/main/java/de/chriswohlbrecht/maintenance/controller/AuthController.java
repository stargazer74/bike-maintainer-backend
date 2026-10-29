package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AuthApi;
import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.TokenRequest;
import de.chriswohlbrecht.maintenance.api.model.UpdateCurrentUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.IAuthComponent;
import de.chriswohlbrecht.maintenance.component.IRegistrationComponent;
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

import java.util.Map;

/**
 * Login, logout, current user and its settings (language), registration and email verification. Password changes
 * are not implemented yet and answer 501 via the generated defaults (backend#7, #8).
 *
 * <p>Registration exists only in portal mode; in household mode its endpoints answer 404 before reaching this
 * controller ({@code HouseholdSecurityConfiguration}), hence the optional dependency.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final IAuthComponent authComponent;
    private final ObjectProvider<IRegistrationComponent> registrationComponent;
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

    /** The generated interface has no servlet parameters; login/logout need them to manage the session. */
    private static ServletRequestAttributes servletRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }
}
