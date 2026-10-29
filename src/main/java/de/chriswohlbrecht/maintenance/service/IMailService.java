package de.chriswohlbrecht.maintenance.service;

/** Sends plain-text emails. Callers build subject and body (already translated); the sender is configured. */
public interface IMailService {

    /** Never throws because of delivery problems; they are only logged. */
    void send(String to, String subject, String body);
}
