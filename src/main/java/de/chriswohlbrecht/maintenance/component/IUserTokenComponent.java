package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;

import java.time.Duration;
import java.util.Optional;

/** One-time tokens for links in emails (email verification, password reset). */
public interface IUserTokenComponent {

    /**
     * Creates a token and invalidates all open tokens of the same user and type.
     *
     * @return the token for the link (URL-safe Base64, 32 random bytes); only its hash is stored
     */
    String issue(AppUser user, UserTokenType type, Duration validity);

    /**
     * Uses up a token.
     *
     * @return the owner, or empty if the token is unknown, of another type, expired or already used
     */
    Optional<AppUser> consume(String token, UserTokenType type);

    /** Deletes used and expired tokens; returns how many. */
    int deleteExpired();
}
