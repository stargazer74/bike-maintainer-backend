package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.service.IMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountMailComponentImplTest {

    private final IMailService mailService = mock(IMailService.class);
    private final IMailQuotaComponent mailQuotaComponent = mock(IMailQuotaComponent.class);
    private AccountMailComponentImpl component;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        component = new AccountMailComponentImpl(mailService, mailQuotaComponent, messageSource,
                new UserLocaleComponentImpl(),
                new AccountMailProperties("noreply@example.org", 100, "https://app.example.org/"));
        when(mailQuotaComponent.tryReserve()).thenReturn(true);
    }

    @Test
    void verificationMailInUserLanguage() {
        component.sendVerification(user("en"), "abc");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("rider@example.org"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Please confirm your email address");
        assertThat(body.getValue()).contains("https://app.example.org/email-bestaetigen?token=abc");
    }

    @Test
    void verificationMailInGermanByDefault() {
        component.sendVerification(user("de"), "abc");

        verify(mailService).send(eq("rider@example.org"), eq("Bitte bestätige deine E-Mail-Adresse"), anyString());
    }

    @Test
    void existsMailContainsLoginAndResetLinks() {
        component.sendRegistrationExists(user("de"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("rider@example.org"), eq("Registrierungsversuch mit deiner E-Mail-Adresse"),
                body.capture());
        assertThat(body.getValue())
                .contains("https://app.example.org/anmelden")
                .contains("https://app.example.org/passwort-vergessen");
    }

    @Test
    void noMailWhenQuotaExhausted() {
        when(mailQuotaComponent.tryReserve()).thenReturn(false);

        component.sendVerification(user("de"), "abc");

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    private static AppUser user(String language) {
        return AppUser.builder().email("rider@example.org").role(UserRole.USER).language(language).build();
    }
}
