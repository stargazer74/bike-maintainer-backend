package de.chriswohlbrecht.maintenance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Local development: emails are not sent but written to the log, so links (e.g. verification) can be opened. */
@Slf4j
@Service
@Profile("local")
public class LoggingMailService implements IMailService {

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email not sent (profile local)\nTo: {}\nSubject: {}\n\n{}", to, subject, body);
    }
}
