package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mail settings from application.properties, applied to the mail auto-configuration only (no application
 * context of the whole service).
 */
class MailConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=test")
            .withConfiguration(AutoConfigurations.of(
                    MailSenderAutoConfiguration.class, MailHealthContributorAutoConfiguration.class));

    /** Without timeouts a stalled SMTP server would block the async mail threads forever. */
    @Test
    void smtpConnectionHasTimeouts() {
        runner.run(context -> assertThat(context.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "10000")
                .containsEntry("mail.smtp.writetimeout", "10000"));
    }

    /** The health endpoint must not depend on (or probe) the SMTP server. */
    @Test
    void healthDoesNotCheckMailServer() {
        runner.run(context -> assertThat(context).doesNotHaveBean("mailHealthContributor"));
        // Counter-check: the same setup would register it if the property allowed it.
        runner.withPropertyValues("management.health.mail.enabled=true")
                .run(context -> assertThat(context).hasBean("mailHealthContributor"));
    }
}
