package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.service.IMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailComponentImpl implements IAccountMailComponent {

    private final IMailService mailService;
    private final IMailQuotaComponent mailQuotaComponent;
    private final MessageSource messageSource;
    private final IUserLocaleComponent userLocaleComponent;
    private final AccountMailProperties mailProperties;

    @Override
    public void sendVerification(AppUser user, String token) {
        send(user, "mail.verification", mailProperties.linkBaseUrl() + "/email-bestaetigen?token=" + token);
    }

    @Override
    public void sendRegistrationExists(AppUser user) {
        send(user, "mail.registrationExists", mailProperties.linkBaseUrl() + "/anmelden",
                mailProperties.linkBaseUrl() + "/passwort-vergessen");
    }

    private void send(AppUser user, String key, Object... bodyArguments) {
        if (!mailQuotaComponent.tryReserve()) {
            log.warn("Daily email limit reached, {} email not sent", key);
            return;
        }
        Locale locale = userLocaleComponent.localeOf(user);
        mailService.send(user.getEmail(),
                messageSource.getMessage(key + ".subject", null, locale),
                messageSource.getMessage(key + ".body", bodyArguments, locale));
    }
}
