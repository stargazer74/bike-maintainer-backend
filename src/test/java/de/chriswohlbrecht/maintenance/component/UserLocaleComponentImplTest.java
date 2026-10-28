package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UserLocaleComponentImplTest {

    private final UserLocaleComponentImpl userLocaleComponent = new UserLocaleComponentImpl();

    @Test
    void localeOf_german_returnsGermany() {
        assertThat(userLocaleComponent.localeOf(AppUser.builder().language("de").build())).isEqualTo(Locale.GERMANY);
    }

    @Test
    void localeOf_english_returnsUk() {
        assertThat(userLocaleComponent.localeOf(AppUser.builder().language("en").build())).isEqualTo(Locale.UK);
    }

    @Test
    void localeOf_missingOrUnknownLanguage_fallsBackToGerman() {
        assertThat(userLocaleComponent.localeOf(AppUser.builder().language(null).build())).isEqualTo(Locale.GERMANY);
        assertThat(userLocaleComponent.localeOf(AppUser.builder().language("fr").build())).isEqualTo(Locale.GERMANY);
    }

    @Test
    void resolveLanguage_requestedLanguageWins() {
        assertThat(userLocaleComponent.resolveLanguage(Language.EN, "de-DE,de;q=0.9")).isEqualTo(Language.EN);
    }

    @Test
    void resolveLanguage_withoutRequest_usesAcceptLanguage() {
        assertThat(userLocaleComponent.resolveLanguage(null, "en-US,en;q=0.9,de;q=0.8")).isEqualTo(Language.EN);
    }

    @Test
    void resolveLanguage_skipsUnsupportedLanguagesInOrderOfQuality() {
        assertThat(userLocaleComponent.resolveLanguage(null, "fr-FR;q=1.0, de;q=0.5, en;q=0.7"))
                .isEqualTo(Language.EN);
    }

    @Test
    void resolveLanguage_ignoresLanguagesWithQualityZero() {
        assertThat(userLocaleComponent.resolveLanguage(null, "en;q=0, fr")).isEqualTo(Language.DE);
    }

    @Test
    void resolveLanguage_missingWildcardOrMalformedHeader_fallsBackToGerman() {
        assertThat(userLocaleComponent.resolveLanguage(null, null)).isEqualTo(Language.DE);
        assertThat(userLocaleComponent.resolveLanguage(null, " ")).isEqualTo(Language.DE);
        assertThat(userLocaleComponent.resolveLanguage(null, "*")).isEqualTo(Language.DE);
        assertThat(userLocaleComponent.resolveLanguage(null, "en;q=abc")).isEqualTo(Language.DE);
    }
}
