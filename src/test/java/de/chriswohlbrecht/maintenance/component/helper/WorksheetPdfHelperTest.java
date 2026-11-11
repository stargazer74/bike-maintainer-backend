package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class WorksheetPdfHelperTest {

    private static final LocalDate PRINTED = LocalDate.of(2026, 11, 10);

    private final WorksheetPdfHelper helper = new WorksheetPdfHelper(messageSource());

    @Test
    void valveClearance_german_showsHeaderTablePerCylinderAndFillInFields() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(4, 4), WorksheetPrefill.none(), PRINTED, Locale.GERMANY));

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

    @ParameterizedTest
    @EnumSource(WorksheetTemplate.class)
    void everyTemplate_footerShowsTheWebsiteInBothLanguages(WorksheetTemplate template) throws IOException {
        ValveLayout valves = new ValveLayout(4, 4);

        assertThat(text(helper.generate(vehicle(), template, valves, WorksheetPrefill.none(), PRINTED, Locale.GERMANY)))
                .contains("Erstellt mit Bike Maintainer · www.bike-maintainer.de · am 10.11.2026");
        assertThat(text(helper.generate(vehicle(), template, valves, WorksheetPrefill.none(), PRINTED, Locale.UK)))
                .contains("Created with Bike Maintainer · www.bike-maintainer.de · on 10 Nov 2026");
    }

    @Test
    void valveClearance_twoValves_oneIntakeAndOneExhaustWithoutNumbers() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(1, 2), WorksheetPrefill.none(), PRINTED, Locale.GERMANY));

        assertThat(count(text, "Einlass")).isEqualTo(1);
        assertThat(count(text, "Auslass")).isEqualTo(1);
        assertThat(text).doesNotContain("Einlass 1").doesNotContain("Auslass 1");
    }

    @Test
    void valveClearance_threeValves_twoIntakesAndOneExhaust() throws IOException {
        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(1, 3), WorksheetPrefill.none(), PRINTED, Locale.GERMANY));

        assertThat(text).contains("Einlass 1").contains("Einlass 2").doesNotContain("Auslass 1");
        assertThat(count(text, "Auslass")).isEqualTo(1);
    }

    @Test
    void valveClearance_eightCylinders_breaksPagesWithoutSplittingACylinder() throws IOException {
        byte[] pdf = helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(8, 4), WorksheetPrefill.none(), PRINTED,
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
        String text = text(helper.generate(vehicle(), WorksheetTemplate.BRAKES, null, WorksheetPrefill.none(), PRINTED, Locale.GERMANY));

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
        String text = text(helper.generate(vehicle(), WorksheetTemplate.CHAIN, null, WorksheetPrefill.none(), PRINTED, Locale.GERMANY));

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
        String text = text(helper.generate(vehicle(), WorksheetTemplate.TIRES, null, WorksheetPrefill.none(), PRINTED, Locale.UK));

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
        byte[] pdf = helper.generate(vehicle(), template, new ValveLayout(2, 4), WorksheetPrefill.none(), PRINTED, Locale.GERMANY);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
        assertThat(text(pdf)).contains("Erledigt von:");
    }

    @Test
    void valveClearance_prefilledWithTargetsAndInstalledShims() throws IOException {
        VehicleSpecification spec = VehicleSpecification.builder()
                .intakeClearanceMin(new BigDecimal("0.10")).intakeClearanceMax(new BigDecimal("0.15"))
                .exhaustClearanceMin(new BigDecimal("0.20")).exhaustClearanceMax(new BigDecimal("0.25"))
                .build();
        WorksheetPrefill prefill = new WorksheetPrefill(spec, Map.of(
                new WorksheetPrefill.Valve(1, ValveKind.INTAKE, 1), new BigDecimal("1.725"),
                new WorksheetPrefill.Valve(2, ValveKind.EXHAUST, 1), new BigDecimal("2.000")));

        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(2, 2),
                prefill, PRINTED, Locale.GERMANY));

        assertThat(count(text, "0,10\u20130,15")).isEqualTo(2);
        assertThat(count(text, "0,20\u20130,25")).isEqualTo(2);
        assertThat(text).contains("1,725").contains("2,00");
    }

    @Test
    void brakesChainAndTires_showTheStoredLimits() throws IOException {
        VehicleSpecification spec = VehicleSpecification.builder()
                .brakePadMinFront(new BigDecimal("1.5")).brakeDiscMinRear(new BigDecimal("4.5"))
                .chainSlackMin(new BigDecimal("25")).chainSlackMax(new BigDecimal("35"))
                .chainWearLinks(20).chainWearMaxLength(new BigDecimal("319"))
                .tirePressureFront(new BigDecimal("2.5")).treadDepthMin(new BigDecimal("1.6"))
                .build();
        WorksheetPrefill prefill = new WorksheetPrefill(spec, Map.of());

        String brakes = text(helper.generate(vehicle(), WorksheetTemplate.BRAKES, null, prefill, PRINTED,
                Locale.GERMANY));
        String chain = text(helper.generate(vehicle(), WorksheetTemplate.CHAIN, null, prefill, PRINTED,
                Locale.GERMANY));
        String tires = text(helper.generate(vehicle(), WorksheetTemplate.TIRES, null, prefill, PRINTED, Locale.UK));

        assertThat(brakes).contains("min. 1,50").contains("min. 4,50");
        assertThat(chain).contains("25,00\u201335,00").contains("max. 319,00").contains("Länge über 20 Glieder (mm)")
                .doesNotContain("___");
        assertThat(tires).contains("2.50").contains("min. 1.60");
        assertThat(count(tires, "min. 1.60")).isEqualTo(2);
    }

    @Test
    void fileName_namesTemplateVehicleAndDateInTheOwnersLanguage() {
        assertThat(helper.fileName(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, PRINTED, Locale.GERMANY))
                .isEqualTo("arbeitsblatt-ventilspiel-die-rote-2026-11-10.pdf");
        assertThat(helper.fileName(vehicle(), WorksheetTemplate.TIRES, PRINTED, Locale.UK))
                .isEqualTo("worksheet-tyres-die-rote-2026-11-10.pdf");
    }

    /** Rendered at 4 px per point, so one point of the layout is easy to address. */
    private static final float SCALE = 4f;

    @ParameterizedTest
    @EnumSource(WorksheetTemplate.class)
    void everyPageCarriesTheMarkersAndItsQrCode(WorksheetTemplate template) throws IOException {
        ValveLayout layout = new ValveLayout(8, 3);
        WorksheetScanLayout.Sheet sheet = WorksheetScanLayout.of(template, layout);
        byte[] pdf = helper.generate(vehicle(), template, layout, WorksheetPrefill.none(), PRINTED, Locale.GERMANY);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(sheet.pages().size());
            for (WorksheetScanLayout.Page page : sheet.pages()) {
                BufferedImage image = render(document, page.number());
                for (float[] marker : WorksheetScanLayout.markers()) {
                    float centre = WorksheetScanLayout.MARKER_SIZE / 2;
                    assertThat(dark(image, marker[0] + centre, marker[1] + centre)).isTrue();
                    assertThat(dark(image, marker[0] - 3f, marker[1] + centre)).isFalse();
                }
                boolean[][] expected = WorksheetPdfHelper.qrModules(sheet.qrContent(42L, page.number()));
                float module = WorksheetScanLayout.QR_SIZE / expected.length;
                for (int y = 0; y < expected.length; y++) {
                    for (int x = 0; x < expected.length; x++) {
                        assertThat(dark(image, WorksheetScanLayout.QR_LEFT + (x + 0.5f) * module,
                                WorksheetScanLayout.QR_TOP + (y + 0.5f) * module))
                                .as("page %d module %d/%d", page.number(), x, y).isEqualTo(expected[y][x]);
                    }
                }
                dump(image, template + "-" + page.number());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(WorksheetTemplate.class)
    void boxesAreDrawnWhereTheLayoutSaysAndAreEmptyInside(WorksheetTemplate template) throws IOException {
        ValveLayout layout = new ValveLayout(4, 4);
        WorksheetScanLayout.Sheet sheet = WorksheetScanLayout.of(template, layout);
        byte[] pdf = helper.generate(vehicle(), template, layout, WorksheetPrefill.none(), PRINTED, Locale.UK);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (WorksheetScanLayout.Page page : sheet.pages()) {
                BufferedImage image = render(document, page.number());
                for (WorksheetScanLayout.Field field : page.fields()) {
                    float middle = field.top() + field.height() / 2;
                    assertThat(line(image, field.left(), middle)).as(field.id() + " left edge").isTrue();
                    assertThat(line(image, field.left() + field.width(), middle)).as(field.id() + " right edge")
                            .isTrue();
                    float firstBox = field.kind() == WorksheetScanLayout.FieldKind.CHECKBOX
                            ? WorksheetScanLayout.CHECKBOX_SIZE : WorksheetScanLayout.DIGIT_WIDTH;
                    assertThat(dark(image, field.left() + firstBox / 2, middle)).as(field.id() + " inside")
                            .isFalse();
                }
            }
        }
    }

    @Test
    void valveClearance_lastInstalledShimIsAHintBelowTheValve() throws IOException {
        WorksheetPrefill prefill = new WorksheetPrefill(null, Map.of(
                new WorksheetPrefill.Valve(1, ValveKind.EXHAUST, 2), new BigDecimal("2.550")));

        String text = text(helper.generate(vehicle(), WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(1, 4),
                prefill, PRINTED, Locale.GERMANY));

        assertThat(text).contains("zuletzt 2,55");
    }

    private static BufferedImage render(PDDocument document, int page) throws IOException {
        return new PDFRenderer(document).renderImage(page - 1, SCALE, ImageType.GRAY);
    }

    private static boolean dark(BufferedImage image, float x, float y) {
        return (image.getRGB(Math.round(x * SCALE), Math.round(y * SCALE)) & 0xff) < 160;
    }

    /** A thin grey box border near (x, y), at most 1/2 point off. */
    private static boolean line(BufferedImage image, float x, float y) {
        int centre = Math.round(x * SCALE);
        int row = Math.round(y * SCALE);
        for (int dx = -2; dx <= 2; dx++) {
            if ((image.getRGB(centre + dx, row) & 0xff) < 200) {
                return true;
            }
        }
        return false;
    }

    /** With -Dbm.dumpWorksheets=<dir>: the rendered pages as PNG, to look at them. */
    private static void dump(BufferedImage image, String name) throws IOException {
        String dir = System.getProperty("bm.dumpWorksheets");
        if (dir != null) {
            ImageIO.write(image, "png", Files.createDirectories(Path.of(dir)).resolve(name + ".png").toFile());
        }
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
                .id(42L)
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
