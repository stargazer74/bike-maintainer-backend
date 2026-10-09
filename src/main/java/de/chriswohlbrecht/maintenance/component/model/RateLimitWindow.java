package de.chriswohlbrecht.maintenance.component.model;

import java.time.Duration;
import java.time.Instant;

/** Mutable attempt counter of one fixed rate-limit window; callers synchronize on the instance. */
public final class RateLimitWindow {

    private final Instant start;
    private int count;

    public RateLimitWindow(Instant start) {
        this.start = start;
    }

    public Instant getStart() {
        return start;
    }

    public int getCount() {
        return count;
    }

    public void increment() {
        count++;
    }

    public boolean isExpired(Instant now, Duration window) {
        return !now.isBefore(start.plus(window));
    }
}
