package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

import java.util.Locale;

/** Maps the preferred language of a user to the locale used for emails and the PDF report. */
public interface IUserLocaleComponent {

    /** Locale for texts addressed to this user: {@code de} → de-DE, {@code en} → en-GB, anything else → de-DE. */
    Locale localeOf(AppUser user);

    /**
     * Language for a new account: the language requested by the client, else the first supported language in the
     * {@code Accept-Language} header, else German.
     *
     * @param requested            language sent by the client, may be {@code null}
     * @param acceptLanguageHeader raw {@code Accept-Language} header, may be {@code null} or malformed
     */
    Language resolveLanguage(Language requested, String acceptLanguageHeader);
}
