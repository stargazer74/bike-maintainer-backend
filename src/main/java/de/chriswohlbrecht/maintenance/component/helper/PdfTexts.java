package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import org.springframework.context.MessageSource;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Translated texts of a PDF document in the language of the vehicle owner. Arguments are preformatted strings, so
 * MessageFormat applies no own number format.
 */
final class PdfTexts {

    private final MessageSource messageSource;
    private final Locale locale;
    private final DateTimeFormatter dateFormatter;
    private final NumberFormat numberFormat;

    PdfTexts(MessageSource messageSource, Locale locale) {
        this.messageSource = messageSource;
        this.locale = locale;
        this.dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        this.numberFormat = NumberFormat.getIntegerInstance(locale);
    }

    String get(String key, String... args) {
        return messageSource.getMessage(key, args, locale);
    }

    String date(LocalDate date) {
        return date.format(dateFormatter);
    }

    String number(Integer value) {
        return value == null ? "" : numberFormat.format(value);
    }

    /** Header and footer of every page: document title, vehicle, creation date and page numbers. */
    PdfFrame frame(String titleKey, Vehicle vehicle, LocalDate createdOn) {
        return new PdfFrame(get(titleKey), vehicle.getName(), get("pdf.footer.created", date(createdOn)),
                (page, total) -> get("pdf.footer.page", String.valueOf(page), String.valueOf(total)));
    }
}
