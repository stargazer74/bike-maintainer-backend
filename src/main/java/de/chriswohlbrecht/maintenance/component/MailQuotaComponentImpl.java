package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException;
import de.chriswohlbrecht.maintenance.persistence.model.MailQuota;
import de.chriswohlbrecht.maintenance.persistence.repository.MailQuotaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MailQuotaComponentImpl implements IMailQuotaComponent {

    private final MailQuotaRepository mailQuotaRepository;
    private final Clock clock;
    private final AccountMailProperties mailProperties;
    /**
     * Today's count for the metric, kept in memory: a metrics scrape must never wait for the database (a database
     * outage would otherwise make the whole scrape time out). Raised by {@link #tryReserve()}, re-read from the
     * database by {@link #refreshUsedGauge()} (day change, restarts).
     */
    private final AtomicInteger usedToday = new AtomicInteger();
    /** Set while the refresh cannot read the database, so the failure is logged once, not every minute. */
    private final AtomicBoolean refreshFailing = new AtomicBoolean();

    public MailQuotaComponentImpl(MailQuotaRepository mailQuotaRepository, Clock clock,
                                  AccountMailProperties mailProperties, MeterRegistry meterRegistry) {
        this.mailQuotaRepository = mailQuotaRepository;
        this.clock = clock;
        this.mailProperties = mailProperties;
        Gauge.builder("bm.mail.quota.used", usedToday, AtomicInteger::get)
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
        boolean reserved = mailQuotaRepository.incrementBelowLimit(today, mailProperties.dailyLimit()) == 1;
        if (reserved) {
            usedToday.incrementAndGet();
        }
        return reserved;
    }

    @Override
    @Transactional(readOnly = true)
    public int sentToday() {
        return mailQuotaRepository.findById(LocalDate.now(clock)).map(MailQuota::getSentCount).orElse(0);
    }

    /** Re-reads today's count for the metric; a database problem keeps the last value and is logged once. */
    @Scheduled(fixedDelayString = "PT1M")
    public void refreshUsedGauge() {
        try {
            usedToday.set(sentToday());
            refreshFailing.set(false);
        } catch (RuntimeException e) {
            if (refreshFailing.compareAndSet(false, true)) {
                log.warn("Email quota metric cannot read the database: {}", e.getClass().getSimpleName());
            }
        }
    }
}
