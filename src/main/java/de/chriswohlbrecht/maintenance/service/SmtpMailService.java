package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends emails via SMTP ({@code spring.mail.*}). Asynchronous, so that answering a registration does not take
 * longer when an email is sent than when none is – otherwise the response time would reveal registered addresses.
 */
@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class SmtpMailService implements IMailService {

    private final JavaMailSender mailSender;
    private final AccountMailProperties mailProperties;
    private final MeterRegistry meterRegistry;

    @Async
    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            countDelivery("success");
        } catch (MailException e) {
            countDelivery("failure");
            // Only the domain: the log must not become a list of email addresses.
            log.error("Sending email to *@{} failed", domainOf(to), e);
        }
    }

    /** Only here is it known whether the SMTP server accepted the email (sending is asynchronous). */
    private void countDelivery(String result) {
        meterRegistry.counter("bm.mail.delivery", "result", result).increment();
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf('@');
        return at < 0 ? "?" : address.substring(at + 1);
    }
}
