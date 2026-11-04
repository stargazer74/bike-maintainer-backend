package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException;
import de.chriswohlbrecht.maintenance.persistence.repository.MailQuotaRepository;
import de.chriswohlbrecht.maintenance.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real SQL against H2 (MariaDB mode) with only the JPA slice, not the whole application. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MailQuotaComponentImplTest {

    private static final AccountMailProperties PROPERTIES =
            new AccountMailProperties("noreply@example.org", 3, "https://app.example.org");

    @Autowired
    private MailQuotaRepository mailQuotaRepository;

    private MutableClock clock;
    private MailQuotaComponentImpl component;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // 22:00 in Berlin, two hours before the day changes
        clock = new MutableClock(Instant.parse("2026-10-29T21:00:00Z"), ZoneId.of("Europe/Berlin"));
        meterRegistry = new SimpleMeterRegistry();
        component = new MailQuotaComponentImpl(mailQuotaRepository, clock, PROPERTIES, meterRegistry);
    }

    @Test
    void allowsUpToLimit() {
        assertThat(component.tryReserve()).isTrue();
        assertThat(component.tryReserve()).isTrue();
        assertThat(component.tryReserve()).isTrue();
        assertThat(component.tryReserve()).isFalse();
    }

    @Test
    void ensureAvailablePassesBelowLimit() {
        component.tryReserve();
        component.tryReserve();

        assertThatCode(component::ensureAvailable).doesNotThrowAnyException();
    }

    @Test
    void ensureAvailableThrowsAtLimitWithRetryAfterUntilMidnight() {
        component.tryReserve();
        component.tryReserve();
        component.tryReserve();

        assertThatThrownBy(component::ensureAvailable)
                .isInstanceOfSatisfying(MailQuotaExceededException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofHours(2)));
    }

    @Test
    void counterResetsNextDay() {
        component.tryReserve();
        component.tryReserve();
        component.tryReserve();
        clock.advance(Duration.ofHours(2));

        assertThatCode(component::ensureAvailable).doesNotThrowAnyException();
        assertThat(component.tryReserve()).isTrue();
    }

    @Test
    void counterSurvivesRestart() {
        component.tryReserve();
        component.tryReserve();
        component.tryReserve();
        MailQuotaComponentImpl afterRestart = new MailQuotaComponentImpl(mailQuotaRepository, clock, PROPERTIES,
                new SimpleMeterRegistry());

        assertThat(afterRestart.tryReserve()).isFalse();
    }

    @Test
    void gaugesReportUsedAndLimit() {
        component.tryReserve();
        component.tryReserve();

        assertThat(meterRegistry.get("bm.mail.quota.used").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("bm.mail.quota.limit").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void usedGaugeIsZeroWithoutRow() {
        assertThat(meterRegistry.get("bm.mail.quota.used").gauge().value()).isZero();
    }

    @Test
    void usedGaugeIsNaNWhenRepositoryFails() {
        MailQuotaRepository failing = mock(MailQuotaRepository.class);
        when(failing.findById(any())).thenThrow(new DataAccessResourceFailureException("database down"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new MailQuotaComponentImpl(failing, clock, PROPERTIES, registry);

        assertThat(registry.get("bm.mail.quota.used").gauge().value()).isNaN();
        assertThat(registry.get("bm.mail.quota.used").gauge().value()).isNaN();
    }
}
