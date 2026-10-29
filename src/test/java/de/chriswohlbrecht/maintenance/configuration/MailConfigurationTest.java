package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MailConfigurationTest {

    @Autowired
    private JavaMailSenderImpl mailSender;

    @Autowired
    private ApplicationContext context;

    /** Without timeouts a stalled SMTP server would block the async mail threads forever. */
    @Test
    void smtpConnectionHasTimeouts() {
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "10000")
                .containsEntry("mail.smtp.writetimeout", "10000");
    }

    /** The health endpoint must not depend on (or probe) the SMTP server. */
    @Test
    void healthDoesNotCheckMailServer() {
        assertThat(context.getBeansOfType(HealthContributor.class)).doesNotContainKey("mailHealthContributor");
    }
}
