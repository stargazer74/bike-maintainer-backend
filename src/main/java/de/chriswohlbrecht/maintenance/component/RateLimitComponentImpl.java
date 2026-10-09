package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.RateLimitWindow;
import de.chriswohlbrecht.maintenance.configuration.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window rate limiter. Sufficient for a single backend instance; counters are lost on
 * restart, which only means a fresh window.
 */
@Component
public class RateLimitComponentImpl implements IRateLimitComponent {

    private static final int CLEANUP_INTERVAL = 1000;

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final Map<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private final AtomicInteger callsSinceCleanup = new AtomicInteger();

    public RateLimitComponentImpl(Clock clock, SecurityProperties securityProperties) {
        this.clock = clock;
        this.maxAttempts = securityProperties.rateLimitMaxAttempts();
        this.window = securityProperties.rateLimitWindow();
    }

    @Override
    public Optional<Duration> tryConsume(String key) {
        Instant now = clock.instant();
        cleanupOccasionally(now);
        RateLimitWindow current = windows.compute(key, (k, existing) ->
                existing == null || existing.isExpired(now, window) ? new RateLimitWindow(now) : existing);
        synchronized (current) {
            if (current.getCount() >= maxAttempts) {
                return Optional.of(Duration.between(now, current.getStart().plus(window)));
            }
            current.increment();
            return Optional.empty();
        }
    }

    private void cleanupOccasionally(Instant now) {
        if (callsSinceCleanup.incrementAndGet() >= CLEANUP_INTERVAL) {
            callsSinceCleanup.set(0);
            windows.values().removeIf(w -> w.isExpired(now, window));
        }
    }
}
