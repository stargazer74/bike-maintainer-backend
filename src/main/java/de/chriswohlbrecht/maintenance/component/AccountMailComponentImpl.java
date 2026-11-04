package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.service.IMailService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailComponentImpl implements IAccountMailComponent {

    private static final String SENT_METRIC = "bm.mail.sent";
    /** Message key → value of the {@code type} tag. */
    private static final Map<String, String> MAIL_TYPES = Map.of(
            "mail.verification", "verification",
            "mail.passwordReset", "password_reset",
            "mail.registrationExists", "registration_exists");


    private final IMailService mailService;
    private final IMailQuotaComponent mailQuotaComponent;
    private final MessageSource messageSource;
    private final IUserLocaleComponent userLocaleComponent;
    private final AccountMailProperties mailProperties;
    private final MeterRegistry meterRegistry;

    @Override
    public void sendVerification(AppUser user, String token) {
        send(user, "mail.verification", mailProperties.linkBaseUrl() + "/email-bestaetigen?token=" + token);
    }

    @Override
    public void sendPasswordReset(AppUser user, String token) {
        send(user, "mail.passwordReset", mailProperties.linkBaseUrl() + "/passwort-zuruecksetzen?token=" + token);
    }

    @Override
    public void sendRegistrationExists(AppUser user) {
        send(user, "mail.registrationExists", mailProperties.linkBaseUrl() + "/anmelden",
                mailProperties.linkBaseUrl() + "/passwort-vergessen");
    }

    private void send(AppUser user, String key, Object... bodyArguments) {
        String type = MAIL_TYPES.get(key);
        if (!mailQuotaComponent.tryReserve()) {
            log.warn("Daily email limit reached, {} email not sent", key);
            meterRegistry.counter(SENT_METRIC, "type", type, "result", "quota_exceeded").increment();
            return;
        }
        meterRegistry.counter(SENT_METRIC, "type", type, "result", "queued").increment();
        Locale locale = userLocaleComponent.localeOf(user);
        mailService.send(user.getEmail(),
                messageSource.getMessage(key + ".subject", null, locale),
                messageSource.getMessage(key + ".body", bodyArguments, locale));
    }
}
