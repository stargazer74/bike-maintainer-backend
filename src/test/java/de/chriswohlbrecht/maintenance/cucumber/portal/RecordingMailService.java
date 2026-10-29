package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.service.IMailService;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces SMTP in the portal Cucumber context: records emails synchronously instead of sending them. */
@Primary
public class RecordingMailService implements IMailService {

    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new SentMail(to, subject, body));
    }

    public List<SentMail> sentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
