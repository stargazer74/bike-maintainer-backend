package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.TokenRequest;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.exception.InvalidPasswordException;
import de.chriswohlbrecht.maintenance.exception.InvalidTokenException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link #register} deliberately runs without a surrounding transaction: when a parallel request registers the same
 * address first, the failed insert must not roll back the notice email that is sent instead.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class RegistrationComponentImpl implements IRegistrationComponent {

    static final Duration VERIFICATION_VALIDITY = Duration.ofHours(24);

    private final IAltchaComponent altchaComponent;
    private final IMailQuotaComponent mailQuotaComponent;
    private final IAccountMailComponent accountMailComponent;
    private final IUserTokenComponent userTokenComponent;
    private final IUserLocaleComponent userLocaleComponent;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String createChallengeJson() {
        return altchaComponent.createChallengeJson();
    }

    /** Checked before the ALTCHA solution, so a rejected password does not use up the solved challenge. */
    @Override
    public void register(RegisterRequest request, String acceptLanguage) {
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > InitialAdminComponentImpl.MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException(
                    "Password must not exceed " + InitialAdminComponentImpl.MAX_PASSWORD_BYTES + " bytes (UTF-8)");
        }
        altchaComponent.verify(request.getAltcha());
        String honeypot = request.getWebsite();
        if (honeypot != null && !honeypot.isBlank()) {
            log.info("Registration ignored: honeypot field filled in");
            return;
        }
        mailQuotaComponent.ensureAvailable();

        String email = normalize(request.getEmail());
        // Hashed in both cases, so that a registered address does not answer faster than a new one.
        String passwordHash = passwordEncoder.encode(request.getPassword());
        Optional<AppUser> existing = appUserRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            accountMailComponent.sendRegistrationExists(existing.get());
            return;
        }

        AppUser user;
        try {
            user = appUserRepository.saveAndFlush(AppUser.builder()
                    .email(email)
                    .passwordHash(passwordHash)
                    .role(UserRole.USER)
                    .emailVerified(false)
                    .language(userLocaleComponent.resolveLanguage(request.getLanguage(), acceptLanguage).getValue())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Registered in parallel by another request: treat like an existing address.
            appUserRepository.findByEmailIgnoreCase(email).ifPresent(accountMailComponent::sendRegistrationExists);
            return;
        }
        accountMailComponent.sendVerification(user,
                userTokenComponent.issue(user, UserTokenType.EMAIL_VERIFICATION, VERIFICATION_VALIDITY));
    }

    @Override
    @Transactional
    public void verifyEmail(TokenRequest request) {
        AppUser user = userTokenComponent.consume(request.getToken(), UserTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new InvalidTokenException("Token is invalid, expired or already used"));
        user.setEmailVerified(true);
        appUserRepository.save(user);
    }

    @Override
    public void resendVerification(EmailRequest request) {
        altchaComponent.verify(request.getAltcha());
        mailQuotaComponent.ensureAvailable();
        appUserRepository.findByEmailIgnoreCase(normalize(request.getEmail()))
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .ifPresent(user -> accountMailComponent.sendVerification(user,
                        userTokenComponent.issue(user, UserTokenType.EMAIL_VERIFICATION, VERIFICATION_VALIDITY)));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
