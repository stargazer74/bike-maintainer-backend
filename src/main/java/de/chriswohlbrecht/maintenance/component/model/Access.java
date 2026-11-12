package de.chriswohlbrecht.maintenance.component.model;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;

import java.time.LocalDate;

/**
 * What an account may do.
 *
 * @param status          trial, paid subscription, read-only or unrestricted
 * @param until           {@code TRIAL}/{@code ACTIVE}: last day with full access (inclusive); {@code EXPIRED}: the
 *                        last day access was granted; null for {@code FULL} and when unknown
 * @param hasSubscription whether the account has or had a subscription with the payment provider
 */
public record Access(AccessStatus status, LocalDate until, boolean hasSubscription) {

    /** Household mode and administrators: no restrictions. */
    public static Access full(boolean hasSubscription) {
        return new Access(AccessStatus.FULL, null, hasSubscription);
    }

    public boolean canWrite() {
        return status != AccessStatus.EXPIRED;
    }
}
