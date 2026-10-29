package de.chriswohlbrecht.maintenance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingMailServiceTest {

    @Test
    void logsRecipientSubjectAndBody(CapturedOutput output) {
        new LoggingMailService().send("rider@example.org", "Bitte bestätigen",
                "Link: https://app.example.org/email-bestaetigen?token=abc");

        assertThat(output.getOut())
                .contains("rider@example.org")
                .contains("Bitte bestätigen")
                .contains("https://app.example.org/email-bestaetigen?token=abc");
    }
}
