package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAuthenticationProviderImplTest {

    private static final String EMAIL = "rider@example.org";
    private static final String PASSWORD = "correct horse battery";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private static final String PASSWORD_HASH = PASSWORD_ENCODER.encode(PASSWORD);

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private IAccountRetentionComponent accountRetentionComponent;

    private LoginAuthenticationProviderImpl provider;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        provider = new LoginAuthenticationProviderImpl(appUserRepository, PASSWORD_ENCODER, CLOCK,
                new SecurityProperties(10, Duration.ofMinutes(15), 3, Duration.ofMinutes(15), Duration.ofDays(90)), meterRegistry,
                new StaticListableBeanFactory(Map.of("retention", accountRetentionComponent))
                        .getBeanProvider(IAccountRetentionComponent.class));
    }

    @Test
    void authenticate_validCredentials_returnsSessionUserWithRole() {
        AppUser user = user().build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        Authentication result = provider.authenticate(token("  Rider@Example.org ", PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(new SessionUser(42L, EMAIL));
        assertThat(result.getName()).isEqualTo("42");
        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        verify(appUserRepository, never()).save(any());
        verify(accountRetentionComponent).keepAfterLogin(user);
    }

    /** Household mode has no retention component; the login provider works without it. */
    @Test
    void authenticate_withoutRetention_succeeds() {
        LoginAuthenticationProviderImpl withoutRetention = new LoginAuthenticationProviderImpl(appUserRepository,
                PASSWORD_ENCODER, CLOCK,
                new SecurityProperties(10, Duration.ofMinutes(15), 3, Duration.ofMinutes(15), Duration.ofDays(90)),
                meterRegistry, new StaticListableBeanFactory().getBeanProvider(IAccountRetentionComponent.class));
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().build()));

        assertThat(withoutRetention.authenticate(token(EMAIL, PASSWORD)).isAuthenticated()).isTrue();
    }

    @Test
    void authenticate_wrongPassword_doesNotCountAsLoginForRetention() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(accountRetentionComponent);
    }

    @Test
    void authenticate_unknownEmail_failsWithBadCredentials() {
        when(appUserRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(token("nobody@example.org", PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_accountWithoutPassword_failsWithBadCredentials() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user().passwordHash(null).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_wrongPassword_countsFailedAttempt() {
        AppUser user = user().build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void authenticate_reachingMaxFailedAttempts_locksAccount() {
        AppUser user = user().failedLoginAttempts(2).build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusMinutes(15));
    }

    @Test
    void authenticate_lockedAccount_rejectsEvenCorrectPassword() {
        AppUser user = user().failedLoginAttempts(3).lockedUntil(NOW.plusMinutes(5)).build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter())
                        .isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void authenticate_expiredLock_correctPassword_resetsCounters() {
        AppUser user = user().failedLoginAttempts(3).lockedUntil(NOW.minusMinutes(1)).build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        provider.authenticate(token(EMAIL, PASSWORD));

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void authenticate_successAfterFailures_resetsCounter() {
        AppUser user = user().failedLoginAttempts(2).build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        provider.authenticate(token(EMAIL, PASSWORD));

        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(appUserRepository).save(user);
    }

    @Test
    void authenticate_deactivatedAccount_correctPassword_reportsAccountDisabled() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().active(false).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)))
                .isInstanceOf(AccountUnusableException.class)
                .extracting("code").isEqualTo(AccountUnusableException.ACCOUNT_DISABLED);
    }

    @Test
    void authenticate_deactivatedAccount_wrongPassword_revealsNothing() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().active(false).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_unverifiedEmail_correctPassword_reportsEmailNotVerified() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user().emailVerified(false).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)))
                .isInstanceOf(AccountUnusableException.class)
                .extracting("code").isEqualTo(AccountUnusableException.EMAIL_NOT_VERIFIED);
    }

    private static UsernamePasswordAuthenticationToken token(String email, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    }

    @Test
    void countsSuccessfulLogin() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().build()));

        provider.authenticate(token(EMAIL, PASSWORD));

        assertThat(loginCount("success")).isEqualTo(1.0);
    }

    @Test
    void countsUnknownEmailAsBadCredentials() {
        when(appUserRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(token("nobody@example.org", PASSWORD)));

        assertThat(loginCount("bad_credentials")).isEqualTo(1.0);
    }

    @Test
    void countsWrongPasswordAsBadCredentials() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong")));

        assertThat(loginCount("bad_credentials")).isEqualTo(1.0);
        assertThat(loginCount("success")).isZero();
    }

    @Test
    void countsLoginWhileLockedAsLocked() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user().failedLoginAttempts(3).lockedUntil(NOW.plusMinutes(5)).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)));

        assertThat(loginCount("locked")).isEqualTo(1.0);
    }

    @Test
    void countsDisabledAccount() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user().active(false).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)));

        assertThat(loginCount("disabled")).isEqualTo(1.0);
    }

    @Test
    void countsUnverifiedAccount() {
        when(appUserRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user().emailVerified(false).build()));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, PASSWORD)));

        assertThat(loginCount("unverified")).isEqualTo(1.0);
    }

    @Test
    void countsLockoutWhenMaxAttemptsReached() {
        AppUser user = user().build();
        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong")));
        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong")));
        assertThat(meterRegistry.counter("bm.auth.lockout").count()).isZero();

        assertThatThrownBy(() -> provider.authenticate(token(EMAIL, "wrong")));

        assertThat(meterRegistry.counter("bm.auth.lockout").count()).isEqualTo(1.0);
    }

    @Test
    void registersLoginCountersUpFront() {
        assertThat(meterRegistry.find("bm.auth.login").counters()).hasSize(5);
        assertThat(meterRegistry.find("bm.auth.lockout").counters()).hasSize(1);
    }

    private double loginCount(String result) {
        return meterRegistry.counter("bm.auth.login", "result", result).count();
    }

    private static AppUser.AppUserBuilder user() {
        return AppUser.builder()
                .id(42L)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .role(UserRole.USER)
                .active(true)
                .emailVerified(true)
                .failedLoginAttempts(0);
    }
}
