package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Email/password login against {@code app_user}.
 *
 * <p>Order of checks matters: the password is verified first, and only then are lockout-independent account
 * states (deactivated, unverified email) reported, so that someone without the password learns nothing
 * about the account. Unknown emails still run a password hash comparison to keep response times similar.
 */
@Component
public class LoginAuthenticationProviderImpl implements AuthenticationProvider {

    static final String BAD_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecurityProperties securityProperties;
    /** Hash of a random value, compared against for unknown emails so they take as long as known ones. */
    private final String dummyHash;

    public LoginAuthenticationProviderImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                                           Clock clock, SecurityProperties securityProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.securityProperties = securityProperties;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String email = String.valueOf(authentication.getPrincipal()).trim().toLowerCase(Locale.ROOT);
        String password = String.valueOf(authentication.getCredentials());

        Optional<AppUser> found = appUserRepository.findByEmailIgnoreCase(email);
        if (found.isEmpty() || found.get().getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyHash);
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        AppUser user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        boolean lockExpired = false;
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                throw new TooManyRequestsException(Duration.between(now, user.getLockedUntil()));
            }
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            lockExpired = true;
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailedAttempt(user, now);
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new AccountUnusableException(AccountUnusableException.ACCOUNT_DISABLED, "Account is deactivated");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AccountUnusableException(AccountUnusableException.EMAIL_NOT_VERIFIED,
                    "Email address is not verified");
        }

        if (user.getFailedLoginAttempts() > 0 || lockExpired) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        }

        SessionUser principal = new SessionUser(user.getId(), user.getEmail());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /** Saved immediately (no surrounding transaction), so the counter survives the exception thrown afterwards. */
    private void registerFailedAttempt(AppUser user, LocalDateTime now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.lockoutMaxFailedAttempts()) {
            user.setLockedUntil(now.plus(securityProperties.lockoutDuration()));
        }
        appUserRepository.save(user);
    }
}
