package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
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
}
