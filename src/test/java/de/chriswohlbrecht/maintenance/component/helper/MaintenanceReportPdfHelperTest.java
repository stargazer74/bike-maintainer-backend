package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceReportPdfHelperTest {

    private final MaintenanceReportPdfHelper helper = new MaintenanceReportPdfHelper(messageSource());

    private static final MaintenanceReportEntry ENTRY = new MaintenanceReportEntry(
            LocalDate.of(2026, 10, 9), 12500, "Ölwechsel durchgeführt", List.of("Ölwechsel", "Bremsen prüfen"));

    @Test
    void generate_returnsPdfDocument() {
        byte[] pdf = helper.generate(vehicle(), List.of(ENTRY), Locale.GERMANY);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generate_german_usesGermanLabelsAndFormats() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(ENTRY), Locale.GERMANY));

        assertThat(text)
                .contains("Wartungshistorie")
                .contains("Typ: Motorrad")
                .contains("Marke/Modell: Honda CB500")
                .contains("Baujahr: 2019")
                .contains("Aktueller Kilometerstand: 23.456 km")
                .contains("Durchgeführte Wartungen")
                .contains("09.10.2026 – 12.500 km")
                .contains("Aufgaben: Ölwechsel, Bremsen prüfen")
                .contains("Notizen: Ölwechsel durchgeführt")
                .doesNotContain("MOTORCYCLE");
    }

    @Test
    void generate_english_usesEnglishLabelsAndFormats() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(ENTRY), Locale.UK));

        assertThat(text)
                .contains("Maintenance history")
                .contains("Type: Motorcycle")
                .contains("Make/model: Honda CB500")
                .contains("Model year: 2019")
                .contains("Current mileage: 23,456 km")
                .contains("Performed maintenance")
                .contains("9 Oct 2026 – 12,500 km")
                .contains("Tasks: Ölwechsel, Bremsen prüfen")
                .contains("Notes: Ölwechsel durchgeführt")
                .doesNotContain("Wartung");
    }

    @Test
    void generate_withoutEntries_saysSoInTheUsersLanguage() throws IOException {
        Vehicle car = vehicle();
        car.setType(VehicleType.CAR);

        assertThat(text(helper.generate(car, List.of(), Locale.GERMANY)))
                .contains("Typ: Auto").contains("Keine Wartungen erfasst.");
        assertThat(text(helper.generate(car, List.of(), Locale.UK)))
                .contains("Type: Car").contains("No maintenance recorded.");
    }

    @Test
    void generate_withCharactersTheFontLacks_doesNotFail() throws IOException {
        MaintenanceReportEntry withEmoji = new MaintenanceReportEntry(
                LocalDate.of(2026, 10, 9), 12500, "Kette gespannt 👍", List.of("Kette → schmieren"));

        assertThat(text(helper.generate(vehicle(), List.of(withEmoji), Locale.GERMANY)))
                .contains("Notizen: Kette gespannt ?")
                .contains("Aufgaben: Kette ? schmieren");
    }

    private static Vehicle vehicle() {
        return Vehicle.builder()
                .name("Die Rote")
                .type(VehicleType.MOTORCYCLE)
                .make("Honda")
                .model("CB500")
                .modelYear(2019)
                .currentMileage(23456)
                .build();
    }

    /** Same settings as the application (spring.messages.*). */
    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
