package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.service.IMailService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
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
            "mail.registrationExists", "registration_exists",
            "mail.deletionNotice", "deletion_notice",
            "mail.cancellationNotice", "cancellation_notice");


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

    @Override
    public void sendDeletionNotice(AppUser user, LocalDate deletionDay) {
        Locale locale = userLocaleComponent.localeOf(user);
        String date = deletionDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
        deliver(user, "mail.deletionNotice", locale, date, mailProperties.linkBaseUrl() + "/anmelden");
    }

    @Override
    public void sendCancellationNotice(AppUser admin, CancellationNotice notice) {
        Locale locale = userLocaleComponent.localeOf(admin);
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale);
        String refund = notice.refund() == null || notice.currency() == null
                ? messageSource.getMessage("mail.cancellationNotice.refundUnknown", null, locale)
                : money(notice.refund(), notice.currency(), locale);
        deliver(admin, "mail.cancellationNotice", locale, String.valueOf(notice.userId()), notice.subscriptionId(),
                notice.receivedOn().format(dates), notice.endsOn().format(dates), notice.paidUntil().format(dates),
                refund);
    }

    /** E.g. 2005 + "eur" → "20,05 €" (de) or "€20.05" (en). */
    private static String money(long minorUnits, String currencyCode, Locale locale) {
        Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        return format.format(BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits()));
    }

    private void send(AppUser user, String key, Object... bodyArguments) {
        if (!mailQuotaComponent.tryReserve()) {
            log.warn("Daily email limit reached, {} email not sent", key);
            meterRegistry.counter(SENT_METRIC, "type", MAIL_TYPES.get(key), "result", "quota_exceeded").increment();
            return;
        }
        deliver(user, key, userLocaleComponent.localeOf(user), bodyArguments);
    }

    private void deliver(AppUser user, String key, Locale locale, Object... bodyArguments) {
        meterRegistry.counter(SENT_METRIC, "type", MAIL_TYPES.get(key), "result", "queued").increment();
        mailService.send(user.getEmail(),
                messageSource.getMessage(key + ".subject", bodyArguments, locale),
                messageSource.getMessage(key + ".body", bodyArguments, locale));
    }
}
