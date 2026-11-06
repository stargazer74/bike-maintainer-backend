package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AdminApi;
import de.chriswohlbrecht.maintenance.api.model.UpdateUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.IAdminUserComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User administration. {@code /api/v1/admin/**} requires ROLE_ADMIN ({@code SecurityConfiguration}); in household mode
 * the paths answer 404 before reaching this controller, hence the optional dependency.
 */
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final ObjectProvider<IAdminUserComponent> adminUserComponent;

    @Override
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(adminUserComponent.getObject().listUsers());
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(Long userId, UpdateUserRequest updateUserRequest) {
        return adminUserComponent.getObject().updateUser(userId, updateUserRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
