package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Field;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.FieldKind;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Page;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorksheetScanLayoutTest {

    /** The contract with the app; the app's test resources hold an identical copy. */
    private static final String CONTRACT = "worksheet-scan-layout-v" + WorksheetScanLayout.VERSION + ".json";

    static Stream<Arguments> allSheets() {
        return sheets().stream().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("allSheets")
    void everyFieldLiesInsideTheMarkerFrameAndBelowTheQrCode(Sheet sheet) {
        float frameLeft = WorksheetScanLayout.MARKER_INSET + WorksheetScanLayout.MARKER_SIZE;
        float frameRight = WorksheetScanLayout.PAGE_WIDTH - frameLeft;
        float frameBottom = WorksheetScanLayout.PAGE_HEIGHT - frameLeft;
        float qrBottom = WorksheetScanLayout.QR_TOP + WorksheetScanLayout.QR_SIZE;
        for (Page page : sheet.pages()) {
            for (Field field : page.fields()) {
                assertThat(field.left()).as(field.id()).isGreaterThan(frameLeft);
                assertThat(field.left() + field.width()).as(field.id()).isLessThan(frameRight);
                assertThat(field.top()).as(field.id()).isGreaterThan(qrBottom);
                assertThat(field.top() + field.height()).as(field.id())
                        .isLessThanOrEqualTo(WorksheetScanLayout.CONTENT_BOTTOM).isLessThan(frameBottom);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allSheets")
    void fieldsNeverOverlapAndIdsAreUnique(Sheet sheet) {
        Set<String> ids = new HashSet<>();
        for (Page page : sheet.pages()) {
            List<Field> fields = page.fields();
            for (int i = 0; i < fields.size(); i++) {
                assertThat(ids.add(fields.get(i).id())).as(fields.get(i).id()).isTrue();
                for (int j = i + 1; j < fields.size(); j++) {
                    assertThat(overlap(fields.get(i), fields.get(j)))
                            .as(fields.get(i).id() + " / " + fields.get(j).id()).isFalse();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allSheets")
    void boxesStayInsideTheirTableCell(Sheet sheet) {
        for (Page page : sheet.pages()) {
            for (Field field : page.fields()) {
                if (field.kind() == FieldKind.DIGITS) {
                    float[] columns = field.id().startsWith("valves.") ? WorksheetScanLayout.VALVE_COLUMNS
                            : WorksheetScanLayout.CHECK_COLUMNS;
                    int column = columnOf(columns, field.left());
                    assertThat(field.left() + field.width()).as(field.id())
                            .isLessThan(WorksheetScanLayout.columnLeft(columns, column + 1));
                }
            }
        }
    }

    @Test
    void valveClearance_cylindersPerPageAndFillInPlacement() {
        assertThat(WorksheetScanLayout.cylindersPerPage(4)).isEqualTo(3);
        assertThat(WorksheetScanLayout.cylindersPerPage(3)).isEqualTo(3);
        assertThat(WorksheetScanLayout.cylindersPerPage(2)).isEqualTo(4);

        Sheet two = WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(2, 4));
        assertThat(two.pages()).hasSize(1);
        assertThat(two.pages().getFirst().fillInTop()).isNotNull();

        // 3 cylinders fill page 1, the fill-in fields get a page of their own.
        Sheet three = WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(3, 4));
        assertThat(three.pages()).hasSize(2);
        assertThat(three.pages().get(0).fillInTop()).isNull();
        assertThat(three.pages().get(1).fields()).isEmpty();
        assertThat(three.pages().get(1).fillInTop()).isEqualTo(WorksheetScanLayout.CONTENT_TOP);

        Sheet eight = WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(8, 4));
        assertThat(eight.pages()).hasSize(3);
        assertThat(eight.pages().get(2).fields()).hasSize(2 * 4 * 3);
        assertThat(eight.pages().get(2).fillInTop()).isNotNull();
    }

    @Test
    void valveClearance_requiresTheLayout() {
        assertThatThrownBy(() -> WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkSheets_onePageWithTheFieldsTheAppReads() {
        assertThat(ids(WorksheetScanLayout.of(WorksheetTemplate.BRAKES, null)))
                .containsExactly("brakes.padFront", "brakes.discFront", "brakes.padRear", "brakes.discRear");
        assertThat(ids(WorksheetScanLayout.of(WorksheetTemplate.CHAIN, null)))
                .containsExactly("chain.slack", "chain.wearLength", "chain.cleaned", "chain.lubricated");
        assertThat(ids(WorksheetScanLayout.of(WorksheetTemplate.TIRES, null)))
                .containsExactly("tires.pressureFront", "tires.treadFront", "tires.dotFront", "tires.pressureRear",
                        "tires.treadRear", "tires.dotRear");
    }

    @Test
    void qrContent_carriesTemplateLayoutVehicleAndPageButNoPersonalData() {
        assertThat(WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(8, 4)).qrContent(42L, 2))
                .isEqualTo("BMWS;v=1;t=VALVE_CLEARANCE;id=42;c=8;vpc=4;p=2/3");
        assertThat(WorksheetScanLayout.of(WorksheetTemplate.TIRES, null).qrContent(7L, 1))
                .isEqualTo("BMWS;v=1;t=TIRES;id=7;p=1/1");
    }

    /** Demo sheets of the trial (backend#138): own prefix, no vehicle ID, everything else as usual. */
    @Test
    void demoQrContent_hasNoVehicleId() {
        assertThat(WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE, new ValveLayout(8, 4)).demoQrContent(2))
                .isEqualTo("BMWD;v=1;t=VALVE_CLEARANCE;c=8;vpc=4;p=2/3");
        assertThat(WorksheetScanLayout.of(WorksheetTemplate.TIRES, null).demoQrContent(1))
                .isEqualTo("BMWD;v=1;t=TIRES;p=1/1");
    }

    /**
     * Positions must not change silently: the app reads photos with the same numbers. If this fails after an
     * intended change, raise {@link WorksheetScanLayout#VERSION}, regenerate the file with
     * {@code -Dbm.writeScanLayout=true} and copy it into the app.
     */
    @Test
    void positionsMatchTheContractWithTheApp() throws IOException {
        String json = contractJson();
        if (Boolean.getBoolean("bm.writeScanLayout")) {
            Files.writeString(Path.of("src/test/resources", CONTRACT), json, StandardCharsets.UTF_8);
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONTRACT)) {
            assertThat(input).as(CONTRACT).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(json);
        }
    }

    private static List<Sheet> sheets() {
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(WorksheetScanLayout.of(WorksheetTemplate.BRAKES, null));
        sheets.add(WorksheetScanLayout.of(WorksheetTemplate.CHAIN, null));
        sheets.add(WorksheetScanLayout.of(WorksheetTemplate.TIRES, null));
        for (int cylinders = 1; cylinders <= 8; cylinders++) {
            for (int valves = 2; valves <= 4; valves++) {
                sheets.add(WorksheetScanLayout.of(WorksheetTemplate.VALVE_CLEARANCE,
                        new ValveLayout(cylinders, valves)));
            }
        }
        return sheets;
    }

    /** One field per line, so a diff shows exactly what moved. */
    private static String contractJson() {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"version\": ").append(WorksheetScanLayout.VERSION).append(",\n");
        json.append("  \"page\": {\"width\": ").append(format(WorksheetScanLayout.PAGE_WIDTH))
                .append(", \"height\": ").append(format(WorksheetScanLayout.PAGE_HEIGHT)).append("},\n");
        json.append("  \"markerSize\": ").append(format(WorksheetScanLayout.MARKER_SIZE)).append(",\n");
        json.append("  \"markers\": [");
        List<float[]> markers = WorksheetScanLayout.markers();
        for (int i = 0; i < markers.size(); i++) {
            json.append(i == 0 ? "" : ", ").append('[').append(format(markers.get(i)[0])).append(", ")
                    .append(format(markers.get(i)[1])).append(']');
        }
        json.append("],\n");
        json.append("  \"qr\": {\"left\": ").append(format(WorksheetScanLayout.QR_LEFT))
                .append(", \"top\": ").append(format(WorksheetScanLayout.QR_TOP))
                .append(", \"size\": ").append(format(WorksheetScanLayout.QR_SIZE)).append("},\n");
        json.append("  \"digit\": {\"width\": ").append(format(WorksheetScanLayout.DIGIT_WIDTH))
                .append(", \"height\": ").append(format(WorksheetScanLayout.DIGIT_HEIGHT))
                .append(", \"separator\": ").append(format(WorksheetScanLayout.SEPARATOR_WIDTH)).append("},\n");
        json.append("  \"checkboxSize\": ").append(format(WorksheetScanLayout.CHECKBOX_SIZE)).append(",\n");
        json.append("  \"sheets\": [\n");
        List<Sheet> sheets = sheets();
        for (int s = 0; s < sheets.size(); s++) {
            Sheet sheet = sheets.get(s);
            json.append("    {\"template\": \"").append(sheet.template().name()).append('"');
            if (sheet.valves() != null) {
                json.append(", \"cylinders\": ").append(sheet.valves().cylinders())
                        .append(", \"valvesPerCylinder\": ").append(sheet.valves().valvesPerCylinder());
            }
            json.append(", \"pages\": [\n");
            for (int p = 0; p < sheet.pages().size(); p++) {
                Page page = sheet.pages().get(p);
                json.append("      {\"number\": ").append(page.number()).append(", \"fields\": [");
                List<Field> fields = page.fields();
                for (int f = 0; f < fields.size(); f++) {
                    Field field = fields.get(f);
                    json.append(f == 0 ? "\n" : ",\n").append("        {\"id\": \"").append(field.id())
                            .append("\", \"kind\": \"").append(field.kind().name())
                            .append("\", \"left\": ").append(format(field.left()))
                            .append(", \"top\": ").append(format(field.top()))
                            .append(", \"integerDigits\": ").append(field.integerDigits())
                            .append(", \"decimalDigits\": ").append(field.decimalDigits()).append('}');
                }
                json.append(fields.isEmpty() ? "" : "\n      ").append("]}")
                        .append(p < sheet.pages().size() - 1 ? ",\n" : "\n");
            }
            json.append("    ]}").append(s < sheets.size() - 1 ? ",\n" : "\n");
        }
        return json.append("  ]\n}\n").toString();
    }

    private static List<String> ids(Sheet sheet) {
        assertThat(sheet.pages()).hasSize(1);
        return sheet.pages().getFirst().fields().stream().map(Field::id).toList();
    }

    private static boolean overlap(Field a, Field b) {
        return a.left() < b.left() + b.width() && b.left() < a.left() + a.width()
                && a.top() < b.top() + b.height() && b.top() < a.top() + a.height();
    }

    private static int columnOf(float[] columns, float left) {
        int column = 0;
        while (column + 1 < columns.length && WorksheetScanLayout.columnLeft(columns, column + 1) <= left) {
            column++;
        }
        return column;
    }
}
