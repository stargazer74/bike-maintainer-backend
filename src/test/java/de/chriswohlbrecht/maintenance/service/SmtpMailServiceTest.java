package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SmtpMailService mailService = new SmtpMailService(mailSender,
            new AccountMailProperties("noreply@bike-maintainer.de", 100, "https://app.example.org"), meterRegistry);

    @Test
    void sendsPlainTextMailFromConfiguredSender() {
        mailService.send("rider@example.org", "Betreff", "Text mit Link");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@bike-maintainer.de");
        assertThat(message.getTo()).containsExactly("rider@example.org");
        assertThat(message.getSubject()).isEqualTo("Betreff");
        assertThat(message.getText()).isEqualTo("Text mit Link");
    }

    @Test
    void swallowsMailException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailService.send("rider@example.org", "Betreff", "Text")).doesNotThrowAnyException();
    }

    @Test
    void countsSuccessfulDelivery() {
        mailService.send("rider@example.org", "Betreff", "Text");

        assertThat(deliveryCount("success")).isEqualTo(1.0);
        assertThat(deliveryCount("failure")).isZero();
    }

    @Test
    void countsFailedDelivery() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailService.send("rider@example.org", "Betreff", "Text");

        assertThat(deliveryCount("failure")).isEqualTo(1.0);
        assertThat(deliveryCount("success")).isZero();
    }

    private double deliveryCount(String result) {
        return meterRegistry.counter("bm.mail.delivery", "result", result).count();
    }

    @Test
    void registersDeliveryCountersUpFront() {
        assertThat(meterRegistry.find("bm.mail.delivery").counters()).hasSize(2);
        assertThat(deliveryCount("failure")).isZero();
    }
}
