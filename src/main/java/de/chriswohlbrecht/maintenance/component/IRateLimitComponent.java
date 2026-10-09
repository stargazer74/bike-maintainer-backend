package de.chriswohlbrecht.maintenance.component;

import java.time.Duration;
import java.util.Optional;

public interface IRateLimitComponent {

    /**
     * Counts one attempt for the given key (e.g. endpoint + client IP).
     *
     * @return empty if the attempt is allowed, otherwise the time until the next attempt is allowed
     */
    Optional<Duration> tryConsume(String key);
}
