package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.type.MaterialUnit;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceReportPdfHelperTest {

    private final MaintenanceReportPdfHelper helper = new MaintenanceReportPdfHelper(messageSource());

    private static final LocalDate CREATED = LocalDate.of(2026, 10, 30);

    private static final MaintenanceReportEntry ENTRY = new MaintenanceReportEntry(
            LocalDate.of(2026, 10, 9), 12500, "Ölwechsel durchgeführt", List.of("Ölwechsel", "Bremsen prüfen"));

    @Test
    void generate_returnsPdfDocument() {
        byte[] pdf = helper.generate(vehicle(), List.of(ENTRY), CREATED, Locale.GERMANY);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generate_german_usesGermanLabelsAndFormats() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(ENTRY), CREATED, Locale.GERMANY));

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
        String text = text(helper.generate(vehicle(), List.of(ENTRY), CREATED, Locale.UK));

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

        assertThat(text(helper.generate(car, List.of(), CREATED, Locale.GERMANY)))
                .contains("Typ: Auto").contains("Keine Wartungen erfasst.");
        assertThat(text(helper.generate(car, List.of(), CREATED, Locale.UK)))
                .contains("Type: Car").contains("No maintenance recorded.");
    }

    @Test
    void generate_withCharactersTheFontLacks_doesNotFail() throws IOException {
        MaintenanceReportEntry withEmoji = new MaintenanceReportEntry(
                LocalDate.of(2026, 10, 9), 12500, "Kette gespannt 👍", List.of("Kette → schmieren"));

        assertThat(text(helper.generate(vehicle(), List.of(withEmoji), CREATED, Locale.GERMANY)))
                .contains("Notizen: Kette gespannt ?")
                .contains("Aufgaben: Kette → schmieren");
    }

    @Test
    void generate_long_everyPageHasHeaderAndFooterInTheUsersLanguage() throws IOException {
        List<MaintenanceReportEntry> entries = IntStream.range(0, 40)
                .mapToObj(i -> new MaintenanceReportEntry(LocalDate.of(2020, 1, 1).plusMonths(i), 1000 * i,
                        "Notiz " + i, List.of("Ölwechsel")))
                .toList();

        assertEveryPage(helper.generate(vehicle(), entries, CREATED, Locale.GERMANY),
                "Wartungshistorie", "Erstellt mit Bike Maintainer am 30.10.2026", "Seite %d von %d");
        assertEveryPage(helper.generate(vehicle(), entries, CREATED, Locale.UK),
                "Maintenance history", "Created with Bike Maintainer on 30 Oct 2026", "Page %d of %d");
    }

    private static final MaintenanceReportEntry COSTLY = new MaintenanceReportEntry(
            LocalDate.of(2026, 10, 9), 12500, null, List.of("Ölwechsel"),
            List.of(new ReportMaterial("Ölfilter", "HF-204", new BigDecimal("1.000"), MaterialUnit.PIECE,
                            new BigDecimal("12.50")),
                    new ReportMaterial("Motoröl", null, new BigDecimal("0.5"), MaterialUnit.LITER, null)),
            new BigDecimal("37.25"), new BigDecimal("49.75"), "EUR");

    @Test
    void generate_withCosts_german_showsMaterialsLaborAndTotals() throws IOException {
        String text = normalized(text(helper.generate(vehicle(), List.of(COSTLY), CREATED, Locale.GERMANY)));

        assertThat(text)
                .contains("Ölfilter – Teilenr. HF-204 – 1 Stk. – 12,50 €")
                .contains("Motoröl – 0,5 l")
                .contains("Arbeitskosten: 37,25 €")
                .contains("Summe: 49,75 €")
                .contains("Gesamtsummen");
    }

    @Test
    void generate_withCosts_english_formatsAmountsAndUnits() throws IOException {
        String text = normalized(text(helper.generate(vehicle(), List.of(COSTLY), CREATED, Locale.UK)));

        assertThat(text)
                .contains("Ölfilter – Part no. HF-204 – 1 pcs – €12.50")
                .contains("Labor cost: €37.25")
                .contains("Total: €49.75")
                .contains("Grand totals");
    }

    @Test
    void generate_grandTotalsPerCurrencySortedByCode() throws IOException {
        MaintenanceReportEntry chf = new MaintenanceReportEntry(LocalDate.of(2026, 10, 10), 13000, null, List.of(),
                List.of(), new BigDecimal("100.00"), new BigDecimal("100.00"), "CHF");
        String text = normalized(text(helper.generate(vehicle(), List.of(COSTLY, ENTRY, COSTLY, chf), CREATED,
                Locale.GERMANY)));

        assertThat(text).containsSubsequence("Gesamtsummen", "100,00 CHF", "99,50 €");
    }

    @Test
    void generate_entryWithoutCosts_showsNoCostBlockAndNoGrandTotals() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(ENTRY), CREATED, Locale.GERMANY));

        assertThat(text).doesNotContain("Summe").doesNotContain("Gesamtsummen").doesNotContain("Arbeitskosten")
                .doesNotContain("Material:");
    }

    /** NumberFormat uses (narrow) no-break spaces between amount and currency; the font text contains them as is. */
    private static String normalized(String text) {
        return text.replace('\u00A0', ' ').replace('\u202F', ' ');
    }

    private static void assertEveryPage(byte[] pdf, String documentType, String footer, String pageLabel)
            throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int total = document.getNumberOfPages();
            assertThat(total).isGreaterThan(1);
            for (int page = 1; page <= total; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document))
                        .contains("Bike Maintainer")
                        .contains(documentType)
                        .contains("Die Rote")
                        .contains(footer)
                        .contains(pageLabel.formatted(page, total));
            }
        }
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
