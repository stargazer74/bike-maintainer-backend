package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class WorksheetPdfHelperTest {

    private static final LocalDate PRINTED = LocalDate.of(2026, 11, 10);

    private final WorksheetPdfHelper helper = new WorksheetPdfHelper(messageSource());

    @Test
    void valveClearance_german_showsHeaderTablePerCylinderAndFillInFields() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(4, 4),
                PRINTED, Locale.GERMANY));

        assertThat(text)
                .contains("Arbeitsblatt Ventilspiel")
                .contains("Die Rote")
                .contains("Marke/Modell: Honda CB500")
                .contains("Baujahr: 2019")
                .contains("Aktueller Kilometerstand: 23.456 km")
                .contains("Erstellt am: 10.11.2026")
                .contains("Motor kalt")
                .contains("OT-Markierung geprüft")
                .contains("Zylinder 1")
                .contains("Zylinder 4")
                .doesNotContain("Zylinder 5")
                .contains("Soll (mm)")
                .contains("Shim alt")
                .contains("Shim neu")
                .contains("Datum:")
                .contains("Kilometerstand:")
                .contains("Erledigt von:")
                .contains("Notizen:");
        // 4 cylinders × 4 valves: two intakes and two exhausts per cylinder.
        assertThat(count(text, "Einlass 1")).isEqualTo(4);
        assertThat(count(text, "Einlass 2")).isEqualTo(4);
        assertThat(count(text, "Auslass 1")).isEqualTo(4);
        assertThat(count(text, "Auslass 2")).isEqualTo(4);
    }

    @Test
    void valveClearance_twoValves_oneIntakeAndOneExhaustWithoutNumbers() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(1, 2),
                PRINTED, Locale.GERMANY));

        assertThat(count(text, "Einlass")).isEqualTo(1);
        assertThat(count(text, "Auslass")).isEqualTo(1);
        assertThat(text).doesNotContain("Einlass 1").doesNotContain("Auslass 1");
    }

    @Test
    void valveClearance_threeValves_twoIntakesAndOneExhaust() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(1, 3),
                PRINTED, Locale.GERMANY));

        assertThat(text).contains("Einlass 1").contains("Einlass 2").doesNotContain("Auslass 1");
        assertThat(count(text, "Auslass")).isEqualTo(1);
    }

    @Test
    void valveClearance_eightCylinders_breaksPagesWithoutSplittingACylinder() throws IOException {
        byte[] pdf = helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(8, 4), PRINTED,
                Locale.GERMANY);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                // Every cylinder heading on a page is followed by all four of its valves on the same page.
                assertThat(count(pageText, "Einlass 1")).isEqualTo(count(pageText, "Zylinder "));
                assertThat(count(pageText, "Auslass 2")).isEqualTo(count(pageText, "Zylinder "));
            }
        }
        assertThat(count(text(pdf), "Zylinder ")).isEqualTo(8);
    }

    @Test
    void brakes_german_frontRearAndBrakeFluid() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.BRAKES, null, PRINTED, Locale.GERMANY));

        assertThat(text)
                .contains("Arbeitsblatt Bremsen")
                .contains("Vorne")
                .contains("Hinten")
                .contains("Bremsflüssigkeit")
                .contains("Zuletzt gewechselt")
                .contains("Erledigt von:")
                .doesNotContain("Zylinder");
        assertThat(count(text, "Belagstärke (mm)")).isEqualTo(2);
        assertThat(count(text, "Scheibendicke (mm)")).isEqualTo(2);
        assertThat(count(text, "Zustand Scheibe")).isEqualTo(2);
    }

    @Test
    void chain_german_measurementConditionAndBoxes() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.CHAIN, null, PRINTED, Locale.GERMANY));

        assertThat(text)
                .contains("Arbeitsblatt Kette")
                .contains("Durchhang (mm)")
                .contains("Länge über ___ Glieder (mm)")
                .contains("Ritzel")
                .contains("Kettenrad")
                .contains("Kette gereinigt")
                .contains("Kette geschmiert");
    }

    @Test
    void tires_english_usesEnglishTexts() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.TIRES, null, PRINTED, Locale.UK));

        assertThat(text)
                .contains("Worksheet tyres")
                .contains("Current mileage: 23,456 km")
                .contains("Front")
                .contains("Rear")
                .contains("Done by:")
                .doesNotContain("Vorne");
        assertThat(count(text, "Pressure (bar)")).isEqualTo(2);
        assertThat(count(text, "Tread depth (mm)")).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(WorksheetTemplate.class)
    void everyTemplateFitsOnePageAndShowsTheFillInFields(WorksheetTemplate template) throws IOException {
        byte[] pdf = helper.generate(vehicle(), template, new ValveLayout(2, 4), PRINTED, Locale.GERMANY);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
        assertThat(text(pdf)).contains("Erledigt von:");
    }

    @Test
    void fileName_namesTemplateVehicleAndDateInTheOwnersLanguage() {
        assertThat(helper.fileName(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, PRINTED, Locale.GERMANY))
                .isEqualTo("arbeitsblatt-ventilspiel-die-rote-2026-11-10.pdf");
        assertThat(helper.fileName(vehicle(), WorksheetTemplate.TIRES, PRINTED, Locale.UK))
                .isEqualTo("worksheet-tyres-die-rote-2026-11-10.pdf");
    }

    private static int count(String text, String part) {
        Matcher matcher = Pattern.compile(Pattern.quote(part)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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
