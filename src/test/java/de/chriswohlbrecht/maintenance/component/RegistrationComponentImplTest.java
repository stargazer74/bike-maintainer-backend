package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.TokenRequest;
import de.chriswohlbrecht.maintenance.exception.InvalidTokenException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationComponentImplTest {

    @Mock
    private IAltchaComponent altchaComponent;
    @Mock
    private IMailQuotaComponent mailQuotaComponent;
    @Mock
    private IAccountMailComponent accountMailComponent;
    @Mock
    private IUserTokenComponent userTokenComponent;
    @Mock
    private IUserLocaleComponent userLocaleComponent;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private RegistrationComponentImpl component;

    @Test
    void concurrentInsertIsTreatedAsExistingAddress() {
        AppUser registeredInParallel = AppUser.builder().id(7L).email("new@example.org").role(UserRole.USER).build();
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");
        when(userLocaleComponent.resolveLanguage(any(), any())).thenReturn(Language.DE);
        when(appUserRepository.findByEmailIgnoreCase("new@example.org"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registeredInParallel));
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("uk_app_user_email"));

        assertThatCode(() -> component.register(new RegisterRequest()
                .email("new@example.org").password("correct-horse-battery").altcha("solved"), null))
                .doesNotThrowAnyException();

        verify(accountMailComponent).sendRegistrationExists(registeredInParallel);
        verify(accountMailComponent, never()).sendVerification(any(), anyString());
    }

    @Test
    void countsStartedRegistration() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");
        when(userLocaleComponent.resolveLanguage(any(), any())).thenReturn(Language.DE);
        when(appUserRepository.findByEmailIgnoreCase("new@example.org")).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        component.register(new RegisterRequest()
                .email("new@example.org").password("correct-horse-battery").altcha("solved"), null);

        assertThat(registrationCount("started")).isEqualTo(1.0);
    }

    @Test
    void doesNotCountRegistrationForExistingEmail() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");
        when(appUserRepository.findByEmailIgnoreCase("old@example.org"))
                .thenReturn(Optional.of(AppUser.builder().id(3L).email("old@example.org").role(UserRole.USER).build()));

        component.register(new RegisterRequest()
                .email("old@example.org").password("correct-horse-battery").altcha("solved"), null);

        assertThat(registrationCount("started")).isZero();
    }

    @Test
    void countsVerifiedEmail() {
        AppUser user = AppUser.builder().id(7L).email("new@example.org").role(UserRole.USER).emailVerified(false).build();
        when(userTokenComponent.consume("token", UserTokenType.EMAIL_VERIFICATION)).thenReturn(Optional.of(user));

        component.verifyEmail(new TokenRequest().token("token"));

        assertThat(registrationCount("verified")).isEqualTo(1.0);
    }

    @Test
    void doesNotCountInvalidToken() {
        when(userTokenComponent.consume("token", UserTokenType.EMAIL_VERIFICATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.verifyEmail(new TokenRequest().token("token")))
                .isInstanceOf(InvalidTokenException.class);

        assertThat(registrationCount("verified")).isZero();
    }

    private double registrationCount(String step) {
        return meterRegistry.counter("bm.auth.registration", "step", step).count();
    }
}
