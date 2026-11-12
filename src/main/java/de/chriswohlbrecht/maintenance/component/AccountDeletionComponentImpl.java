package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeleteAccountRequest;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.LastAdminException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.exception.WrongPasswordException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deletes the own account for good (no soft delete). The password is checked like on login: a locked account is
 * rejected, a wrong password counts towards the lockout, so an open session cannot be used to guess it.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class AccountDeletionComponentImpl implements IAccountDeletionComponent {

    private static final String DELETED_METRIC = "bm.accounts.deleted";
    private static final String LOCKOUT_METRIC = "bm.auth.lockout";

    private final ICurrentUserComponent currentUserComponent;
    private final ISessionComponent sessionComponent;
    private final AppUserRepository appUserRepository;
    private final IAccountDataDeletionComponent accountDataDeletionComponent;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public AccountDeletionComponentImpl(ICurrentUserComponent currentUserComponent, ISessionComponent sessionComponent,
                                        AppUserRepository appUserRepository,
                                        IAccountDataDeletionComponent accountDataDeletionComponent,
                                        PasswordEncoder passwordEncoder, SecurityProperties securityProperties,
                                        Clock clock, MeterRegistry meterRegistry) {
        this.currentUserComponent = currentUserComponent;
        this.sessionComponent = sessionComponent;
        this.appUserRepository = appUserRepository;
        this.accountDataDeletionComponent = accountDataDeletionComponent;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        meterRegistry.counter(DELETED_METRIC);
    }

    /** The failed attempt of a wrong password is committed although the exception ends the request. */
    @Override
    @Transactional(noRollbackFor = WrongPasswordException.class)
    public void deleteCurrentUser(DeleteAccountRequest request, String currentSessionId) {
        AppUser user = currentUserComponent.getCurrentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new TooManyRequestsException(Duration.between(now, user.getLockedUntil()));
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailedAttempt(user, now);
            throw new WrongPasswordException("Password is wrong");
        }
        if (isLastActiveAdmin(user)) {
            throw new LastAdminException("The last active admin cannot delete their account");
        }

        accountDataDeletionComponent.deleteWithAllData(user);
        sessionComponent.invalidateOtherSessionsOf(user.getId(), currentSessionId);
        meterRegistry.counter(DELETED_METRIC).increment();
        log.info("User {} deleted their account", user.getId());
    }

    private boolean isLastActiveAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getActive())
                && appUserRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1;
    }

    private void registerFailedAttempt(AppUser user, LocalDateTime now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.lockoutMaxFailedAttempts()) {
            user.setLockedUntil(now.plus(securityProperties.lockoutDuration()));
            meterRegistry.counter(LOCKOUT_METRIC).increment();
        }
        appUserRepository.save(user);
    }
}
