package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException;
import de.chriswohlbrecht.maintenance.persistence.model.MailQuota;
import de.chriswohlbrecht.maintenance.persistence.repository.MailQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailQuotaComponentImpl implements IMailQuotaComponent {

    private final MailQuotaRepository mailQuotaRepository;
    private final Clock clock;
    private final AccountMailProperties mailProperties;

    @Override
    @Transactional(readOnly = true)
    public void ensureAvailable() {
        LocalDate today = LocalDate.now(clock);
        int sent = mailQuotaRepository.findById(today).map(MailQuota::getSentCount).orElse(0);
        if (sent >= mailProperties.dailyLimit()) {
            log.warn("Daily email limit of {} reached", mailProperties.dailyLimit());
            ZonedDateTime now = ZonedDateTime.now(clock);
            throw new MailQuotaExceededException(Duration.between(now, today.plusDays(1).atStartOfDay(now.getZone())));
        }
    }

    /** Atomic in the database, so parallel requests cannot overshoot the limit. */
    @Override
    @Transactional
    public boolean tryReserve() {
        LocalDate today = LocalDate.now(clock);
        mailQuotaRepository.createIfMissing(today);
        return mailQuotaRepository.incrementBelowLimit(today, mailProperties.dailyLimit()) == 1;
    }
}
