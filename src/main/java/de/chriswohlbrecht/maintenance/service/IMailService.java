package de.chriswohlbrecht.maintenance.service;

/** Sends plain-text emails. Callers build subject and body (already translated); the sender is configured. */
public interface IMailService {

    /** Never throws because of delivery problems; they are only logged. */
    void send(String to, String subject, String body);

    /**
     * Sends right away and waits for the answer of the mail server, for the few emails whose delivery decides what
     * happens next (e.g. the deletion notice: no deletion without it). Never throws because of delivery problems.
     *
     * @return whether the mail server accepted the email
     */
    boolean sendNow(String to, String subject, String body);
}
