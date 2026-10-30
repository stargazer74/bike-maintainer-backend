package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceChecklistPdfHelperTest {

    private static final LocalDate PRINTED = LocalDate.of(2026, 10, 29);

    private final MaintenanceChecklistPdfHelper helper = new MaintenanceChecklistPdfHelper(messageSource());

    private static final MaintenanceChecklistEntry OVERDUE_OIL = new MaintenanceChecklistEntry(
            "Ölwechsel", "Öl 10W-40, Filter HF204", false, 6000, 12,
            status(MaintenanceTaskStatus.OVERDUE, -300, null, 17700, LocalDate.of(2025, 9, 1)));
    private static final MaintenanceChecklistEntry CHAIN_SOON = new MaintenanceChecklistEntry(
            "Kette spannen", null, false, null, 6,
            status(MaintenanceTaskStatus.DUE_SOON, null, 12, 22000, LocalDate.of(2026, 5, 10)));
    private static final MaintenanceChecklistEntry BRAKES_LATER = new MaintenanceChecklistEntry(
            "Bremsflüssigkeit", null, false, 20000, null,
            status(MaintenanceTaskStatus.OK, 200, null, null, null));

    @Test
    void german_showsHeaderTasksInGivenOrderAndFillInFields() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(OVERDUE_OIL, CHAIN_SOON, BRAKES_LATER), PRINTED,
                Locale.GERMANY));

        assertThat(text)
                .contains("Wartungs-Checkliste")
                .contains("Die Rote")
                .contains("Marke/Modell: Honda CB500")
                .contains("Aktueller Kilometerstand: 23.456 km")
                .contains("Erstellt am: 29.10.2026")
                .contains("Öl 10W-40, Filter HF204")
                .contains("Überfällig um 300 km")
                .contains("Fällig in 12 Tagen")
                .contains("Fällig in 200 km")
                .contains("Zuletzt: 01.09.2025 bei 17.700 km")
                .contains("Zuletzt: noch nie")
                .contains("Alle 6.000 km oder 12 Monate")
                .contains("Alle 6 Monate")
                .contains("Alle 20.000 km")
                .contains("Datum:")
                .contains("Kilometerstand:")
                .contains("Notizen:");
        assertThat(text.indexOf("Ölwechsel")).isLessThan(text.indexOf("Kette spannen"));
        assertThat(text.indexOf("Kette spannen")).isLessThan(text.indexOf("Bremsflüssigkeit"));
    }

    @Test
    void english_usesEnglishTextsAndFormats() throws IOException {
        String text = text(helper.generate(vehicle(), List.of(OVERDUE_OIL, CHAIN_SOON, BRAKES_LATER), PRINTED,
                Locale.UK));

        assertThat(text)
                .contains("Maintenance checklist")
                .contains("Current mileage: 23,456 km")
                .contains("Created on: 29 Oct 2026")
                .contains("Overdue by 300 km")
                .contains("Due in 12 days")
                .contains("Due in 200 km")
                .contains("Last: 1 Sept 2025 at 17,700 km")
                .contains("Last: never")
                .contains("Every 6,000 km or 12 months")
                .contains("Date:")
                .contains("Mileage:")
                .doesNotContain("Fällig");
    }

    @Test
    void singularAndZeroRemainingReadNaturally() throws IOException {
        MaintenanceChecklistEntry oneDay = new MaintenanceChecklistEntry("A", null, false, null, 1,
                status(MaintenanceTaskStatus.DUE_SOON, null, 1, null, null));
        MaintenanceChecklistEntry overdueOneDay = new MaintenanceChecklistEntry("B", null, false, null, 1,
                status(MaintenanceTaskStatus.OVERDUE, null, -1, null, null));
        MaintenanceChecklistEntry dueNow = new MaintenanceChecklistEntry("C", null, false, 1000, null,
                status(MaintenanceTaskStatus.OVERDUE, 0, null, null, null));

        String text = text(helper.generate(vehicle(), List.of(oneDay, overdueOneDay, dueNow), PRINTED,
                Locale.GERMANY));

        assertThat(text).contains("Fällig in 1 Tag").contains("Überfällig seit 1 Tag").contains("Jetzt fällig");
    }

    @Test
    void tasksWithoutDueValuesSayWhy() throws IOException {
        MaintenanceChecklistEntry neverNoAnchor = new MaintenanceChecklistEntry("Erstinspektion", null, true,
                null, null, status(MaintenanceTaskStatus.DUE_SOON, null, null, null, null));
        MaintenanceChecklistEntry noInterval = new MaintenanceChecklistEntry("Sichtprüfung", null, false, null, null,
                status(MaintenanceTaskStatus.OK, null, null, 20000, LocalDate.of(2026, 1, 1)));

        String text = text(helper.generate(vehicle(), List.of(neverNoAnchor, noInterval), PRINTED, Locale.GERMANY));

        assertThat(text).contains("Noch nie durchgeführt").contains("Einmalig").contains("Kein Intervall");
    }

    @Test
    void longDescriptionsAreWrappedCompletely() throws IOException {
        String description = "Bremsbeläge vorne und hinten prüfen, Bremsflüssigkeit wechseln, Bremsscheiben auf "
                + "Riefen und Mindeststärke kontrollieren, Bremssättel reinigen und Kolben gangbar machen, "
                + "Entlüftung an beiden Sätteln.";
        MaintenanceChecklistEntry brakes = new MaintenanceChecklistEntry("Bremsen", description, false, 10000, null,
                status(MaintenanceTaskStatus.OK, 500, null, null, null));

        String text = text(helper.generate(vehicle(), List.of(brakes), PRINTED, Locale.GERMANY));

        assertThat(text.replaceAll("\\s+", " ")).contains(description);
    }

    @Test
    void longVehicleNamesAreWrappedInsteadOfRunningOffThePage() throws IOException {
        Vehicle vehicle = vehicle();
        vehicle.setName("Meine treue Reiseenduro mit Koffersystem, Heizgriffen, Navi und Sturzbügeln aus 2019");

        String text = text(helper.generate(vehicle, List.of(BRAKES_LATER), PRINTED, Locale.GERMANY));

        String firstLineOfName = text.lines().filter(line -> line.startsWith("Meine treue")).findFirst().orElseThrow();
        assertThat(firstLineOfName).isNotEqualTo(vehicle.getName());
        assertThat(text.replaceAll("\\s+", " ")).contains(vehicle.getName());
    }

    @Test
    void fillInFieldsNeverEndUpAloneOnAnExtraPage() throws IOException {
        for (int tasks = 1; tasks <= 25; tasks++) {
            List<MaintenanceChecklistEntry> entries = java.util.Collections.nCopies(tasks, CHAIN_SOON);
            try (PDDocument document = Loader.loadPDF(helper.generate(vehicle(), entries, PRINTED, Locale.GERMANY))) {
                PDFTextStripper stripper = new PDFTextStripper();
                int last = document.getNumberOfPages();
                stripper.setStartPage(last);
                stripper.setEndPage(last);
                assertThat(stripper.getText(document)).as("last page with %d tasks", tasks).contains("Notizen:");
            }
        }
    }

    @Test
    void taskWithoutIntervalSaysSoOnlyOnce() throws IOException {
        MaintenanceChecklistEntry noInterval = new MaintenanceChecklistEntry("Sichtprüfung", null, false, null, null,
                status(MaintenanceTaskStatus.OK, null, null, 20000, LocalDate.of(2026, 1, 1)));

        String text = text(helper.generate(vehicle(), List.of(noInterval), PRINTED, Locale.GERMANY));

        assertThat(text.split("Kein Intervall", -1)).hasSize(2);
    }

    @Test
    void generate_hasHeaderAndFooterInTheUsersLanguage() throws IOException {
        assertThat(text(helper.generate(vehicle(), List.of(BRAKES_LATER), PRINTED, Locale.GERMANY)))
                .contains("Bike Maintainer")
                .contains("Wartungs-Checkliste")
                .contains("Erstellt mit Bike Maintainer am 29.10.2026")
                .contains("Seite 1 von 1");
        assertThat(text(helper.generate(vehicle(), List.of(BRAKES_LATER), PRINTED, Locale.UK)))
                .contains("Maintenance checklist")
                .contains("Created with Bike Maintainer on 29 Oct 2026")
                .contains("Page 1 of 1");
    }

    @Test
    void fileNameUsesASafeVehicleNameAndTheDate() {
        Vehicle vehicle = vehicle();
        vehicle.setName("Die Rote / Ölschleuder!");

        assertThat(helper.fileName(vehicle, PRINTED, Locale.GERMANY))
                .isEqualTo("checkliste-die-rote-oelschleuder-2026-10-29.pdf");
        assertThat(helper.fileName(vehicle, PRINTED, Locale.UK))
                .isEqualTo("checklist-die-rote-oelschleuder-2026-10-29.pdf");
        vehicle.setName("🏍️");
        assertThat(helper.fileName(vehicle, PRINTED, Locale.GERMANY)).isEqualTo("checkliste-2026-10-29.pdf");
    }

    private static MaintenanceTaskStatusResult status(MaintenanceTaskStatus status, Integer kmRemaining,
                                                      Integer daysRemaining, Integer lastMileage,
                                                      LocalDate lastDate) {
        return new MaintenanceTaskStatusResult(status, null, null, kmRemaining, daysRemaining, lastMileage, lastDate);
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
