package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException;
import de.chriswohlbrecht.maintenance.persistence.repository.MailQuotaRepository;
import de.chriswohlbrecht.maintenance.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MailQuotaComponentImplTest {

    private static final AccountMailProperties PROPERTIES =
            new AccountMailProperties("noreply@example.org", 3, "https://app.example.org");

    @Autowired
    private MailQuotaRepository mailQuotaRepository;

    private MutableClock clock;
    private MailQuotaComponentImpl component;

    @BeforeEach
    void setUp() {
        // 22:00 in Berlin, two hours before the day changes
        clock = new MutableClock(Instant.parse("2026-10-29T21:00:00Z"), ZoneId.of("Europe/Berlin"));
        component = new MailQuotaComponentImpl(mailQuotaRepository, clock, PROPERTIES);
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
        MailQuotaComponentImpl afterRestart = new MailQuotaComponentImpl(mailQuotaRepository, clock, PROPERTIES);

        assertThat(afterRestart.tryReserve()).isFalse();
    }
}
