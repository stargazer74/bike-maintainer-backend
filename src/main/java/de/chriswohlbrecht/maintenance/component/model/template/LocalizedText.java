package de.chriswohlbrecht.maintenance.component.model.template;

import java.util.Locale;

/** A text in German and English. */
public record LocalizedText(String de, String en) {

    /** English for the language {@code en}, German for everything else. */
    public String in(Locale locale) {
        if (locale != null && "en".equals(locale.getLanguage())) {
            return en;
        }
        return de;
    }
}
