package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AuthApi;
import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.UpdateCurrentUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.IAuthComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Login, logout, current user and its settings (language). Registration, email verification and password changes
 * are not implemented yet and answer 501 via the generated defaults (backend#14, #7, #8).
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final IAuthComponent authComponent;

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
