package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.UpdateUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
import de.chriswohlbrecht.maintenance.mapper.UserMapper;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Role and active state of accounts. Admins see accounts, never data of other users: vehicles stay scoped to their
 * owner for admins as well.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class AdminUserComponentImpl implements IAdminUserComponent {

    private final AppUserRepository appUserRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final ISessionComponent sessionComponent;
    private final IDeviceLoginComponent deviceLoginComponent;
    private final UserMapper userMapper;
    private final IAccessComponent accessComponent;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        List<AppUser> users = appUserRepository.findAll(Sort.by("createdAt", "id"));
        // One query for the subscriptions of all accounts, not one per account.
        Map<Long, Access> access = accessComponent.accessOf(users);
        return users.stream()
                .map(user -> userMapper.toResponse(user, access.get(user.getId())))
                .toList();
    }

    @Override
    @Transactional
    public Optional<UserResponse> updateUser(Long userId, UpdateUserRequest request) {
        de.chriswohlbrecht.maintenance.api.model.UserRole requestedRole = request.getRole();
        Boolean requestedActive = request.getActive();
        if (requestedRole == null && requestedActive == null) {
            throw new InvalidUserUpdateException("Either role or active must be given");
        }
        Optional<AppUser> found = appUserRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = found.get();
        UserRole newRole = requestedRole != null ? UserRole.valueOf(requestedRole.getValue()) : user.getRole();
        boolean newActive = requestedActive != null ? requestedActive : Boolean.TRUE.equals(user.getActive());
        boolean deactivated = Boolean.TRUE.equals(user.getActive()) && !newActive;
        boolean roleChanged = newRole != user.getRole();

        if (deactivated && userId.equals(currentUserComponent.getCurrentUserId())) {
            throw new InvalidUserUpdateException("Admins cannot deactivate themselves");
        }
        boolean losesActiveAdmin = user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getActive())
                && (deactivated || roleChanged);
        if (losesActiveAdmin && appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new InvalidUserUpdateException("The last active admin cannot be demoted or deactivated");
        }

        user.setRole(newRole);
        user.setActive(newActive);
        AppUser saved = appUserRepository.save(user);
        if (deactivated || roleChanged) {
            sessionComponent.invalidateSessionsOf(userId);
        }
        if (deactivated) {
            deviceLoginComponent.revokeAllOf(userId);
        }
        log.info("Admin {} changed user {}: role {}, active {}", currentUserComponent.getCurrentUserId(), userId,
                newRole, newActive);
        return Optional.of(userMapper.toResponse(saved, accessComponent.accessOf(saved)));
    }
}
