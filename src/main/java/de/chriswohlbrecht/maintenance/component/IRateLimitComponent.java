package de.chriswohlbrecht.maintenance.component;

import java.time.Duration;
import java.util.Optional;

public interface IRateLimitComponent {

    /**
     * Counts one attempt for the given key (e.g. endpoint + client IP).
     *
     * @param group fixed name of the endpoint group (e.g. {@code login}); tag of the metric for rejected attempts,
     *              never a client-specific value
     * @param key   bucket key, e.g. endpoint + client IP
     * @return empty if the attempt is allowed, otherwise the time until the next attempt is allowed
     */
    Optional<Duration> tryConsume(String group, String key);

    /**
     * Takes back one attempt that {@link #tryConsume} counted for the key, e.g. because the login succeeded and
     * was therefore no guess. Never goes below zero; does nothing for an unknown or expired key.
     *
     * @param key bucket key as passed to {@link #tryConsume}
     */
    void refund(String key);
}
