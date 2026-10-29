package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ChangePasswordRequest;
import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.PasswordResetConfirmRequest;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.exception.InvalidPasswordException;
import de.chriswohlbrecht.maintenance.exception.InvalidTokenException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class PasswordComponentImpl implements IPasswordComponent {

    static final Duration RESET_VALIDITY = Duration.ofHours(1);

    private final IAltchaComponent altchaComponent;
    private final IMailQuotaComponent mailQuotaComponent;
    private final IAccountMailComponent accountMailComponent;
    private final IUserTokenComponent userTokenComponent;
    private final ISessionComponent sessionComponent;
    private final ICurrentUserComponent currentUserComponent;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void requestReset(EmailRequest request) {
        altchaComponent.verify(request.getAltcha());
        mailQuotaComponent.ensureAvailable();
        appUserRepository.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .ifPresent(user -> accountMailComponent.sendPasswordReset(user,
                        userTokenComponent.issue(user, UserTokenType.PASSWORD_RESET, RESET_VALIDITY)));
    }

    /** The length is checked before the token is used up, so the user can retry with the same link. */
    @Override
    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        ensureNotTooLong(request.getNewPassword());
        AppUser user = userTokenComponent.consume(request.getToken(), UserTokenType.PASSWORD_RESET)
                .filter(owner -> Boolean.TRUE.equals(owner.getActive()))
                .orElseThrow(() -> new InvalidTokenException("Token is invalid, expired or already used"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setEmailVerified(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);
        sessionComponent.invalidateSessionsOf(user.getId());
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String currentSessionId) {
        ensureNotTooLong(request.getNewPassword());
        AppUser user = currentUserComponent.getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);
        userTokenComponent.revokeOpen(user, UserTokenType.PASSWORD_RESET);
        sessionComponent.invalidateOtherSessionsOf(user.getId(), currentSessionId);
    }

    private static void ensureNotTooLong(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > InitialAdminComponentImpl.MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException(
                    "Password must not exceed " + InitialAdminComponentImpl.MAX_PASSWORD_BYTES + " bytes (UTF-8)");
        }
    }
}
