package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class UserLocaleComponentImpl implements IUserLocaleComponent {

    /** Same regional variants as the frontend (de-DE, en-GB), so dates and numbers look alike in both. */
    private static final Locale GERMAN = Locale.GERMANY;
    private static final Locale ENGLISH = Locale.UK;

    @Override
    public Locale localeOf(AppUser user) {
        return Language.EN.getValue().equals(user.getLanguage()) ? ENGLISH : GERMAN;
    }

    @Override
    public Language resolveLanguage(Language requested, String acceptLanguageHeader) {
        if (requested != null) {
            return requested;
        }
        return fromAcceptLanguage(acceptLanguageHeader);
    }

    /** Ranges come sorted by their quality value; "*" and unsupported languages are skipped. */
    private static Language fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return Language.DE;
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(header);
        } catch (IllegalArgumentException e) {
            return Language.DE;
        }
        for (Locale.LanguageRange range : ranges) {
            if (range.getWeight() == 0) {
                continue;
            }
            String primaryLanguage = range.getRange().split("-", 2)[0];
            for (Language language : Language.values()) {
                if (language.getValue().equals(primaryLanguage)) {
                    return language;
                }
            }
        }
        return Language.DE;
    }
}
