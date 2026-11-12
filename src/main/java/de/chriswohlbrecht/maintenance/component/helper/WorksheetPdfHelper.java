package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Block;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CheckPoint;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CheckRow;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CheckTable;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CheckboxLine;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CylinderTable;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Field;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Page;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Section;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.Sheet;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.ValveRow;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import io.nayuki.qrcodegen.QrCode;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CHECK_COLUMNS;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.CONTENT_LEFT;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.DIGIT_HEIGHT;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.DIGIT_WIDTH;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.HEADER_ROW_HEIGHT;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.LINE_HEIGHT;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.ROW_HEIGHT;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.SEPARATOR_WIDTH;
import static de.chriswohlbrecht.maintenance.component.helper.WorksheetScanLayout.VALVE_COLUMNS;

/**
 * Renders an empty worksheet to fill in by hand in the workshop: vehicle at the top, the measuring tables of the
 * template, and fields for date, mileage, who did the work and notes at the bottom. Stored target values and the
 * shims installed last time are printed; all measured values stay empty.
 *
 * <p>The sheet can also be photographed with the app (Epic #120): every page carries corner markers and a QR code,
 * numbers are written into one box per digit, and all positions come from {@link WorksheetScanLayout}.
 */
@Component
@RequiredArgsConstructor
public class WorksheetPdfHelper {

    private static final PdfFont FONT_TITLE = PdfFont.TITLE;
    private static final PdfFont FONT_HEADING = PdfFont.HEADING;
    private static final PdfFont FONT_TEXT = PdfFont.TEXT;
    private static final float TABLE_FONT_SIZE = 10f;
    private static final int NOTE_LINES = 3;
    private static final float CHECKBOX_LINE_WIDTH = 0.8f;
    private static final float DIGIT_LINE_WIDTH = 0.6f;
    /** The vehicle block ends before the QR code. */
    private static final float HEADER_WIDTH = WorksheetScanLayout.QR_LEFT - CONTENT_LEFT - 12f;

    static final String DEMO_LABEL = "DEMO";
    /** Baseline of {@link #DEMO_LABEL}: about four QR modules below the code, above the content of the sheet. */
    private static final float DEMO_LABEL_BASELINE = WorksheetScanLayout.QR_TOP + WorksheetScanLayout.QR_SIZE + 22f;

    private final MessageSource messageSource;

    /**
     * @param layout  cylinders and valves, only for {@link WorksheetTemplate#VALVE_CLEARANCE} (ignored otherwise)
     * @param prefill target values and installed shims to print instead of empty fields
     * @param locale de-DE or en-GB, see {@code MaintenanceReportPdfHelper#generate}
     */
    public byte[] generate(Vehicle vehicle, WorksheetTemplate template, ValveLayout layout, WorksheetPrefill prefill,
                           LocalDate printDate, Locale locale) {
        return generate(vehicle, template, layout, prefill, printDate, locale, false);
    }

    /**
     * @param demo printed during the trial: the QR code carries no vehicle ID ({@link Sheet#demoQrContent}), so the
     *             app does not take over the values, and "DEMO" is printed below it; the sheet is otherwise the same
     */
    public byte[] generate(Vehicle vehicle, WorksheetTemplate template, ValveLayout layout, WorksheetPrefill prefill,
                           LocalDate printDate, Locale locale, boolean demo) {
        try (PDDocument document = new PDDocument()) {
            PdfTexts texts = new PdfTexts(messageSource, locale);
            Values values = new Values(prefill.specification(), locale, texts);
            String titleKey = "worksheet.title." + template.name();
            Sheet sheet = WorksheetScanLayout.of(template, layout);
            PdfPageWriter writer = new PdfPageWriter(document, texts.frame(titleKey, vehicle, printDate));
            String separator = String.valueOf(DecimalFormatSymbols.getInstance(locale).getDecimalSeparator());
            Renderer renderer = new Renderer(writer, texts, values, prefill, separator);
            for (Page page : sheet.pages()) {
                if (page.number() > 1) {
                    writer.newPage();
                }
                renderer.scanMarks(demo ? sheet.demoQrContent(page.number())
                        : sheet.qrContent(vehicle.getId(), page.number()));
                if (demo) {
                    renderer.demoLabel();
                }
                if (page.number() == 1) {
                    renderer.vehicleBlock(texts.get(titleKey), vehicle, printDate);
                }
                for (Block block : page.blocks()) {
                    renderer.block(block);
                }
                if (page.fillInTop() != null) {
                    renderer.fillInFields(page.fillInTop());
                }
            }
            writer.close();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate worksheet PDF", e);
        }
    }

    /** E.g. {@code arbeitsblatt-ventilspiel-die-rote-2026-11-10.pdf}: only lower-case ASCII, digits and dashes. */
    public String fileName(Vehicle vehicle, WorksheetTemplate template, LocalDate printDate, Locale locale) {
        return PdfVehicleHeader.fileName(messageSource.getMessage("worksheet.fileName." + template.name(), null,
                locale), vehicle, printDate);
    }

    /** The QR code's modules, {@code true} = dark; error correction M survives a bit of dirt or a crease. */
    static boolean[][] qrModules(String content) {
        QrCode code = QrCode.encodeText(content, QrCode.Ecc.MEDIUM);
        boolean[][] modules = new boolean[code.size][code.size];
        for (int y = 0; y < code.size; y++) {
            for (int x = 0; x < code.size; x++) {
                modules[y][x] = code.getModule(x, y);
            }
        }
        return modules;
    }

    /** Draws one page's content; everything at the positions of {@link WorksheetScanLayout}. */
    private record Renderer(PdfPageWriter writer, PdfTexts texts, Values values, WorksheetPrefill prefill,
                            String separator) {

        void scanMarks(String qrContent) throws IOException {
            for (float[] marker : WorksheetScanLayout.markers()) {
                writer.fillRect(marker[0], marker[1], WorksheetScanLayout.MARKER_SIZE,
                        WorksheetScanLayout.MARKER_SIZE);
            }
            boolean[][] modules = qrModules(qrContent);
            float module = WorksheetScanLayout.QR_SIZE / modules.length;
            for (int row = 0; row < modules.length; row++) {
                // Runs of dark modules as one rectangle each: fewer drawing operations, no hairline gaps.
                int column = 0;
                while (column < modules[row].length) {
                    if (!modules[row][column]) {
                        column++;
                        continue;
                    }
                    int start = column;
                    while (column < modules[row].length && modules[row][column]) {
                        column++;
                    }
                    writer.fillRect(WorksheetScanLayout.QR_LEFT + start * module,
                            WorksheetScanLayout.QR_TOP + row * module, (column - start) * module, module);
                }
            }
        }

        /** Below the QR code, outside its quiet zone; the same word in every language. */
        void demoLabel() throws IOException {
            writer.writeTextAt(DEMO_LABEL, FONT_HEADING, 11f, WorksheetScanLayout.QR_LEFT, DEMO_LABEL_BASELINE,
                    WorksheetScanLayout.QR_SIZE, false);
        }

        /** Title, vehicle name, make/model and model year, current mileage, print date – one line each. */
        void vehicleBlock(String title, Vehicle vehicle, LocalDate printDate) throws IOException {
            writer.writeTextAt(title, FONT_TITLE, 18f, CONTENT_LEFT, 100f, HEADER_WIDTH, false);
            writer.writeTextAt(vehicle.getName(), FONT_HEADING, 13f, CONTENT_LEFT, 119f, HEADER_WIDTH, false);
            List<String> details = new ArrayList<>();
            if (vehicle.getMake() != null || vehicle.getModel() != null) {
                String makeModel = String.join(" ",
                        vehicle.getMake() == null ? "" : vehicle.getMake(),
                        vehicle.getModel() == null ? "" : vehicle.getModel()).trim();
                details.add(texts.get("report.makeModel", makeModel));
            }
            if (vehicle.getModelYear() != null) {
                details.add(texts.get("report.modelYear", String.valueOf(vehicle.getModelYear())));
            }
            float baseline = 136f;
            if (!details.isEmpty()) {
                writer.writeTextAt(String.join("   ·   ", details), FONT_TEXT, 11f, CONTENT_LEFT, baseline,
                        HEADER_WIDTH, false);
            }
            baseline += 15f;
            writer.writeTextAt(texts.get("report.currentMileage", texts.number(vehicle.getCurrentMileage())),
                    FONT_TEXT, 11f, CONTENT_LEFT, baseline, HEADER_WIDTH, false);
            writer.writeTextAt(texts.get("checklist.printed", texts.date(printDate)), FONT_TEXT, 11f, CONTENT_LEFT,
                    baseline + 15f, HEADER_WIDTH, false);
        }

        void block(Block block) throws IOException {
            switch (block) {
                case CheckboxLine line -> checkboxLine(line);
                case CheckTable table -> checkTable(table);
                case CylinderTable table -> cylinderTable(table);
            }
        }

        private void checkboxLine(CheckboxLine line) throws IOException {
            float size = WorksheetScanLayout.CHECKBOX_SIZE;
            writer.strokeRect(line.boxLeft(), line.boxTop(), size, size, CHECKBOX_LINE_WIDTH);
            writer.writeTextAt(texts.get(line.labelKey()), FONT_TEXT, 11f, line.boxLeft() + size + 8f,
                    line.top() + LINE_HEIGHT / 2 + 4f, WorksheetScanLayout.CONTENT_WIDTH, false);
        }

        private void checkTable(CheckTable table) throws IOException {
            heading(texts.get(table.section().headingKey()), table.top());
            writer.writeTableRowAt(table.headerTop(), HEADER_ROW_HEIGHT, List.of(
                    PdfCell.of(texts.get("worksheet.column.checkpoint")),
                    PdfCell.of(texts.get("worksheet.column.target")),
                    PdfCell.of(texts.get("worksheet.column.actual")),
                    PdfCell.of(texts.get("worksheet.column.ok"))), CHECK_COLUMNS, FONT_HEADING, TABLE_FONT_SIZE,
                    true);
            for (CheckRow row : table.rows()) {
                writer.writeTableRowAt(row.top(), ROW_HEIGHT, List.of(PdfCell.of(label(row.point())),
                                PdfCell.of(target(table.section(), row.point())), PdfCell.blank(), PdfCell.checkbox()),
                        CHECK_COLUMNS, FONT_TEXT, TABLE_FONT_SIZE, false);
                if (row.actual() != null) {
                    digits(row.actual());
                }
            }
        }

        private void cylinderTable(CylinderTable table) throws IOException {
            heading(texts.get("worksheet.valve.cylinder", String.valueOf(table.cylinder())), table.top());
            writer.writeTableRowAt(table.headerTop(), HEADER_ROW_HEIGHT, List.of(
                    PdfCell.of(texts.get("worksheet.valve.column.valve")),
                    PdfCell.of(texts.get("worksheet.valve.column.target")),
                    PdfCell.of(texts.get("worksheet.valve.column.actual")),
                    PdfCell.of(texts.get("worksheet.valve.column.shimOld")),
                    PdfCell.of(texts.get("worksheet.valve.column.shimNew")),
                    PdfCell.of(texts.get("worksheet.column.ok"))), VALVE_COLUMNS, FONT_HEADING, TABLE_FONT_SIZE, true);
            long intakes = table.rows().stream().filter(row -> row.kind() == ValveKind.INTAKE).count();
            long exhausts = table.rows().size() - intakes;
            VehicleSpecification spec = values.spec();
            String intakeTarget = values.range(spec.getIntakeClearanceMin(), spec.getIntakeClearanceMax());
            String exhaustTarget = values.range(spec.getExhaustClearanceMin(), spec.getExhaustClearanceMax());
            float labelWidth = WorksheetScanLayout.CONTENT_WIDTH * VALVE_COLUMNS[0] - 8f;
            for (ValveRow row : table.rows()) {
                String target = row.kind() == ValveKind.INTAKE ? intakeTarget : exhaustTarget;
                writer.writeTableRowAt(row.top(), ROW_HEIGHT, List.of(PdfCell.blank(),
                                PdfCell.of(target.isEmpty() ? "–" : target), PdfCell.blank(), PdfCell.blank(),
                                PdfCell.blank(), PdfCell.checkbox()),
                        VALVE_COLUMNS, FONT_TEXT, TABLE_FONT_SIZE, false);
                boolean intake = row.kind() == ValveKind.INTAKE;
                // Numbered only if there are two of the kind, like on the measurement form.
                String name = texts.get(intake ? "worksheet.valve.intake" : "worksheet.valve.exhaust")
                        + ((intake ? intakes : exhausts) > 1 ? " " + row.index() : "");
                BigDecimal installed = prefill.installedShims()
                        .get(new WorksheetPrefill.Valve(table.cylinder(), row.kind(), row.index()));
                if (installed == null) {
                    writer.writeTextAt(name, FONT_TEXT, TABLE_FONT_SIZE, CONTENT_LEFT + 4f, row.top() + 19f,
                            labelWidth, false);
                } else {
                    // The shim installed last time below the valve, as a hint for "shim old".
                    writer.writeTextAt(name, FONT_TEXT, TABLE_FONT_SIZE, CONTENT_LEFT + 4f, row.top() + 13f,
                            labelWidth, false);
                    writer.writeTextAt(texts.get("worksheet.valve.lastShim", values.mm(installed)), FONT_TEXT, 8f,
                            CONTENT_LEFT + 4f, row.top() + 24f, labelWidth, true);
                }
                digits(row.measured());
                digits(row.shimOld());
                digits(row.shimNew());
            }
        }

        private void heading(String text, float top) throws IOException {
            writer.writeTextAt(text, FONT_HEADING, 12f, CONTENT_LEFT, top + 14f, WorksheetScanLayout.CONTENT_WIDTH,
                    false);
        }

        /** One box per digit, the decimal separator of the owner's language in the gap. */
        private void digits(Field field) throws IOException {
            float left = field.left();
            for (int digit = 0; digit < field.integerDigits() + field.decimalDigits(); digit++) {
                if (digit == field.integerDigits() && field.decimalDigits() > 0) {
                    writer.writeTextAt(separator, FONT_HEADING, 14f, left + 2f, field.top() + DIGIT_HEIGHT - 2f,
                            SEPARATOR_WIDTH, false);
                    left += SEPARATOR_WIDTH;
                }
                writer.strokeRect(left, field.top(), DIGIT_WIDTH, DIGIT_HEIGHT, DIGIT_LINE_WIDTH);
                left += DIGIT_WIDTH;
            }
        }

        private String label(CheckPoint point) {
            Integer links = values.spec().getChainWearLinks();
            if (point == CheckPoint.CHAIN_WEAR && links != null) {
                return texts.get("worksheet.chain.wearLinks", texts.number(links));
            }
            return texts.get(point.labelKey());
        }

        private String target(Section section, CheckPoint point) {
            VehicleSpecification spec = values.spec();
            boolean front = section == Section.FRONT;
            return switch (point) {
                case PAD_THICKNESS -> values.min(front ? spec.getBrakePadMinFront() : spec.getBrakePadMinRear());
                case DISC_THICKNESS -> values.min(front ? spec.getBrakeDiscMinFront() : spec.getBrakeDiscMinRear());
                case CHAIN_SLACK -> values.range(spec.getChainSlackMin(), spec.getChainSlackMax());
                case CHAIN_WEAR -> values.max(spec.getChainWearMaxLength());
                case TIRE_PRESSURE -> values.bar(front ? spec.getTirePressureFront() : spec.getTirePressureRear());
                case TREAD_DEPTH -> values.min(spec.getTreadDepthMin());
                default -> "";
            };
        }

        /** Date, mileage, done by and notes plus empty note lines; not read by the app. */
        void fillInFields(float top) throws IOException {
            writer.moveTo(top);
            writer.writeFillInLine(texts.get("checklist.fillIn.date"), FONT_TEXT, 11f);
            writer.writeFillInLine(texts.get("checklist.fillIn.mileage"), FONT_TEXT, 11f);
            writer.writeFillInLine(texts.get("worksheet.fillIn.doneBy"), FONT_TEXT, 11f);
            writer.writeFillInLine(texts.get("checklist.fillIn.notes"), FONT_TEXT, 11f);
            for (int i = 0; i < NOTE_LINES; i++) {
                writer.writeFillInLine(null, FONT_TEXT, 11f);
            }
        }
    }

    /** Target values formatted for the sheet: mm with 2–3 decimals, bar with 2; empty if not stored. */
    private record Values(VehicleSpecification spec, Locale locale, PdfTexts texts) {

        String mm(BigDecimal value) {
            return format(value, 3);
        }

        String bar(BigDecimal value) {
            return format(value, 2);
        }

        String min(BigDecimal value) {
            return value == null ? "" : texts.get("worksheet.limit.min", mm(value));
        }

        String max(BigDecimal value) {
            return value == null ? "" : texts.get("worksheet.limit.max", mm(value));
        }

        String range(BigDecimal min, BigDecimal max) {
            if (min == null && max == null) {
                return "";
            }
            if (min == null) {
                return max(max);
            }
            if (max == null) {
                return min(min);
            }
            return mm(min) + "–" + mm(max);
        }

        private String format(BigDecimal value, int maxDecimals) {
            if (value == null) {
                return "";
            }
            NumberFormat format = NumberFormat.getNumberInstance(locale);
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(maxDecimals);
            return format.format(value);
        }
    }
}
