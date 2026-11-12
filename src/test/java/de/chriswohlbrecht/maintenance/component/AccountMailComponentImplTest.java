package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.service.IMailService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.LocalDate;

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
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private AccountMailComponentImpl component;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        component = new AccountMailComponentImpl(mailService, mailQuotaComponent, messageSource,
                new UserLocaleComponentImpl(),
                new AccountMailProperties("noreply@example.org", 100, "https://app.example.org/"), meterRegistry);
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
    void deletionNoticeNamesTheDayAndLinksToLogin() {
        component.sendDeletionNotice(user("de"), LocalDate.of(2027, 12, 1));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("rider@example.org"),
                eq("Dein Konto bei Bike Maintainer wird am 1. Dezember 2027 gelöscht"), body.capture());
        assertThat(body.getValue())
                .contains("am 1. Dezember 2027 gelöscht")
                .contains("https://app.example.org/anmelden")
                .contains("exportieren");
    }

    @Test
    void deletionNoticeInEnglish() {
        component.sendDeletionNotice(user("en"), LocalDate.of(2027, 12, 1));

        verify(mailService).send(eq("rider@example.org"),
                eq("Your Bike Maintainer account will be deleted on 1 December 2027"), anyString());
    }

    /** The retention limits its notices itself, so they never use up the emails for registrations. */
    @Test
    void deletionNoticeDoesNotCountTowardsTheDailyLimit() {
        when(mailQuotaComponent.tryReserve()).thenReturn(false);

        component.sendDeletionNotice(user("de"), LocalDate.of(2027, 12, 1));

        verify(mailService).send(eq("rider@example.org"), anyString(), anyString());
        verify(mailQuotaComponent, never()).tryReserve();
        assertThat(meterRegistry.counter("bm.mail.sent", "type", "deletion_notice", "result", "queued").count())
                .isEqualTo(1.0);
    }

    @Test
    void cancellationNoticeListsEverythingForTheManualRefund() {
        when(mailQuotaComponent.tryReserve()).thenReturn(false);

        component.sendCancellationNotice(user("de"), new CancellationNotice(7L, "sub_1", LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 2, 5), LocalDate.of(2027, 11, 12), 1841L, "eur"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("rider@example.org"), eq("Kündigung nach dem ersten Jahr: Abo sub_1"),
                body.capture());
        assertThat(body.getValue())
                .contains("Konto (ID): 7")
                .contains("Kündigung eingegangen: 5. Januar 2027")
                .contains("Ende nach Monatsfrist: 5. Februar 2027")
                .contains("Bezahlt bis: 12. November 2027")
                .contains("Anteilige Erstattung: 18,41\u00a0€".translateEscapes())
                .contains("Am 5. Februar 2027 das Abo");
        verify(mailQuotaComponent, never()).tryReserve();
    }

    @Test
    void cancellationNoticeInEnglishWithoutKnownPrice() {
        component.sendCancellationNotice(user("en"), new CancellationNotice(7L, "sub_1", LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 2, 5), LocalDate.of(2027, 11, 12), null, null));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("rider@example.org"), eq("Cancellation after the first year: subscription sub_1"),
                body.capture());
        assertThat(body.getValue()).contains("Pro-rata refund: unknown, please calculate it in Polar");
    }

    @Test
    void noMailWhenQuotaExhausted() {
        when(mailQuotaComponent.tryReserve()).thenReturn(false);

        component.sendVerification(user("de"), "abc");

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void countsQueuedVerificationMail() {
        component.sendVerification(user("de"), "abc");

        assertThat(sentCount("verification", "queued")).isEqualTo(1.0);
    }

    @Test
    void countsQueuedMailsByType() {
        component.sendPasswordReset(user("de"), "abc");
        component.sendRegistrationExists(user("de"));

        assertThat(sentCount("password_reset", "queued")).isEqualTo(1.0);
        assertThat(sentCount("registration_exists", "queued")).isEqualTo(1.0);
    }

    @Test
    void countsQuotaExceeded() {
        when(mailQuotaComponent.tryReserve()).thenReturn(false);

        component.sendVerification(user("de"), "abc");

        assertThat(sentCount("verification", "quota_exceeded")).isEqualTo(1.0);
        assertThat(sentCount("verification", "queued")).isZero();
    }

    private double sentCount(String type, String result) {
        return meterRegistry.counter("bm.mail.sent", "type", type, "result", result).count();
    }

    private static AppUser user(String language) {
        return AppUser.builder().email("rider@example.org").role(UserRole.USER).language(language).build();
    }
}
