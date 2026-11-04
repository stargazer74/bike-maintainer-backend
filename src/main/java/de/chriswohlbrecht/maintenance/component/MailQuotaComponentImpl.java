package de.chriswohlbrecht.maintenance.component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import java.util.concurrent.atomic.AtomicBoolean;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException;
import de.chriswohlbrecht.maintenance.persistence.model.MailQuota;
import de.chriswohlbrecht.maintenance.persistence.repository.MailQuotaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Slf4j
@Component
public class MailQuotaComponentImpl implements IMailQuotaComponent {

    private final MailQuotaRepository mailQuotaRepository;
    private final Clock clock;
    private final AccountMailProperties mailProperties;
    /** Set while the used gauge cannot read the database, so the failure is logged once, not on every scrape. */
    private final AtomicBoolean gaugeFailing = new AtomicBoolean();

    public MailQuotaComponentImpl(MailQuotaRepository mailQuotaRepository, Clock clock,
                                  AccountMailProperties mailProperties, MeterRegistry meterRegistry) {
        this.mailQuotaRepository = mailQuotaRepository;
        this.clock = clock;
        this.mailProperties = mailProperties;
        Gauge.builder("bm.mail.quota.used", this, MailQuotaComponentImpl::usedForGauge)
                .description("Emails counted today")
                .register(meterRegistry);
        Gauge.builder("bm.mail.quota.limit", mailProperties, properties -> properties.dailyLimit())
                .description("Daily email limit")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureAvailable() {
        LocalDate today = LocalDate.now(clock);
        int sent = sentToday();
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

    @Override
    @Transactional(readOnly = true)
    public int sentToday() {
        return mailQuotaRepository.findById(LocalDate.now(clock)).map(MailQuota::getSentCount).orElse(0);
    }

    /** Read on every metrics scrape: a database problem yields NaN instead of failing the scrape. */
    private double usedForGauge() {
        try {
            double used = sentToday();
            gaugeFailing.set(false);
            return used;
        } catch (DataAccessException e) {
            if (gaugeFailing.compareAndSet(false, true)) {
                log.warn("Email quota gauge cannot read the database: {}", e.getMessage());
            }
            return Double.NaN;
        }
    }
}
