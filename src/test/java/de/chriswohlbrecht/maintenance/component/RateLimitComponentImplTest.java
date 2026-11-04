package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitComponentImplTest {

    private MutableClock clock;
    private RateLimitComponentImpl rateLimit;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-09T12:00:00Z"));
        meterRegistry = new SimpleMeterRegistry();
        rateLimit = new RateLimitComponentImpl(clock,
                new SecurityProperties(3, Duration.ofMinutes(15), 10, Duration.ofMinutes(15)), meterRegistry);
    }

    @Test
    void tryConsume_withinLimit_allows() {
        assertThat(rateLimit.tryConsume("login", "login|1.2.3.4")).isEmpty();
        assertThat(rateLimit.tryConsume("login", "login|1.2.3.4")).isEmpty();
        assertThat(rateLimit.tryConsume("login", "login|1.2.3.4")).isEmpty();
    }

    @Test
    void tryConsume_overLimit_returnsTimeUntilWindowEnds() {
        consume("login|1.2.3.4", 3);
        clock.advance(Duration.ofMinutes(5));

        assertThat(rateLimit.tryConsume("login", "login|1.2.3.4")).contains(Duration.ofMinutes(10));
    }

    @Test
    void tryConsume_keysAreIndependent() {
        consume("login|1.2.3.4", 3);

        assertThat(rateLimit.tryConsume("login", "login|5.6.7.8")).isEmpty();
        assertThat(rateLimit.tryConsume("login", "register|1.2.3.4")).isEmpty();
    }

    @Test
    void tryConsume_afterWindow_allowsAgain() {
        consume("login|1.2.3.4", 3);
        clock.advance(Duration.ofMinutes(15));

        assertThat(rateLimit.tryConsume("login", "login|1.2.3.4")).isEmpty();
    }

    private void consume(String key, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(rateLimit.tryConsume("login", key)).isEmpty();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void countsRejectedAttemptWithGroup() {
        consume("login|1.2.3.4", 3);

        rateLimit.tryConsume("login", "login|1.2.3.4");

        assertThat(meterRegistry.counter("bm.protection.rate_limited", "group", "login").count()).isEqualTo(1.0);
    }

    @Test
    void doesNotCountAllowedAttempts() {
        consume("login|1.2.3.4", 3);

        assertThat(meterRegistry.find("bm.protection.rate_limited").counters()).isEmpty();
    }
}
