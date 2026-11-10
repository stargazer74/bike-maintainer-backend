package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders an empty worksheet to fill in by hand in the workshop: vehicle at the top, the measuring tables of the
 * template, and fields for date, mileage, who did the work and notes at the bottom. Stored target values and the
 * shims installed last time are printed; all measured values stay empty.
 */
@Component
@RequiredArgsConstructor
public class WorksheetPdfHelper {

    private static final PdfFont FONT_HEADING = PdfFont.HEADING;
    private static final PdfFont FONT_TEXT = PdfFont.TEXT;
    private static final float TABLE_FONT_SIZE = 10f;
    private static final int NOTE_LINES = 3;
    /** Check point, target or limit, actual value or finding, ok. */
    private static final float[] CHECK_COLUMNS = {0.40f, 0.22f, 0.24f, 0.14f};
    /** Valve, target, actual, old shim, new shim, ok. */
    private static final float[] VALVE_COLUMNS = {0.20f, 0.20f, 0.16f, 0.16f, 0.16f, 0.12f};

    private final MessageSource messageSource;

    /**
     * @param layout  cylinders and valves, only for {@link WorksheetTemplate#VALVE_CLEARANCE} (ignored otherwise)
     * @param prefill target values and installed shims to print instead of empty fields
     * @param locale de-DE or en-GB, see {@code MaintenanceReportPdfHelper#generate}
     */
    public byte[] generate(Vehicle vehicle, WorksheetTemplate template, ValveLayout layout, WorksheetPrefill prefill,
                           LocalDate printDate, Locale locale) {
        try (PDDocument document = new PDDocument()) {
            PdfTexts texts = new PdfTexts(messageSource, locale);
            Values values = new Values(prefill.specification(), locale, texts);
            String titleKey = "worksheet.title." + template.name();
            PdfPageWriter writer = new PdfPageWriter(document, texts.frame(titleKey, vehicle, printDate));
            PdfVehicleHeader.write(writer, texts.get(titleKey), vehicle, printDate, texts);
            switch (template) {
                case VALVE_CLEARANCE -> writeValveClearance(writer, layout, prefill, values, texts);
                case BRAKES -> writeBrakes(writer, values, texts);
                case CHAIN -> writeChain(writer, values, texts);
                case TIRES -> writeTires(writer, values, texts);
            }
            writeFillInFields(writer, texts);
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

    private void writeValveClearance(PdfPageWriter writer, ValveLayout layout, WorksheetPrefill prefill, Values values,
                                     PdfTexts texts) throws IOException {
        VehicleSpecification spec = prefill.specification();
        String intakeTarget = values.range(spec.getIntakeClearanceMin(), spec.getIntakeClearanceMax());
        String exhaustTarget = values.range(spec.getExhaustClearanceMin(), spec.getExhaustClearanceMax());
        writer.writeCheckboxItem(texts.get("worksheet.valve.engineCold"), FONT_TEXT, 11f);
        writer.writeCheckboxItem(texts.get("worksheet.valve.tdcChecked"), FONT_TEXT, 11f);
        writer.newLine();
        List<ValveRow> valves = valveRows(layout.valvesPerCylinder(), texts);
        List<PdfCell> header = List.of(
                PdfCell.of(texts.get("worksheet.valve.column.valve")),
                PdfCell.of(texts.get("worksheet.valve.column.target")),
                PdfCell.of(texts.get("worksheet.valve.column.actual")),
                PdfCell.of(texts.get("worksheet.valve.column.shimOld")),
                PdfCell.of(texts.get("worksheet.valve.column.shimNew")),
                PdfCell.of(texts.get("worksheet.column.ok")));
        for (int cylinder = 1; cylinder <= layout.cylinders(); cylinder++) {
            // A cylinder's table is never split across pages.
            writer.ensureSpace(blockHeight(valves.size()));
            writer.writeLine(texts.get("worksheet.valve.cylinder", String.valueOf(cylinder)), FONT_HEADING, 12f);
            writer.writeTableRow(header, VALVE_COLUMNS, FONT_HEADING, TABLE_FONT_SIZE, true);
            for (ValveRow valve : valves) {
                String target = valve.kind() == ValveKind.INTAKE ? intakeTarget : exhaustTarget;
                BigDecimal installed = prefill.installedShims()
                        .get(new WorksheetPrefill.Valve(cylinder, valve.kind(), valve.index()));
                writer.writeTableRow(List.of(PdfCell.of(valve.name()), PdfCell.of(target.isEmpty() ? "\u2013" : target),
                        PdfCell.blank(), PdfCell.of(values.mm(installed)), PdfCell.blank(), PdfCell.checkbox()),
                        VALVE_COLUMNS, FONT_TEXT, TABLE_FONT_SIZE, false);
            }
            writer.newLine();
        }
    }

    /** 2 valves: intake, exhaust; 3: two intakes, one exhaust; 4: two of each. */
    private static List<ValveRow> valveRows(int valvesPerCylinder, PdfTexts texts) {
        String intake = texts.get("worksheet.valve.intake");
        String exhaust = texts.get("worksheet.valve.exhaust");
        List<ValveRow> rows = new ArrayList<>();
        if (valvesPerCylinder == 2) {
            rows.add(new ValveRow(intake, ValveKind.INTAKE, 1));
        } else {
            rows.add(new ValveRow(intake + " 1", ValveKind.INTAKE, 1));
            rows.add(new ValveRow(intake + " 2", ValveKind.INTAKE, 2));
        }
        if (valvesPerCylinder == 4) {
            rows.add(new ValveRow(exhaust + " 1", ValveKind.EXHAUST, 1));
            rows.add(new ValveRow(exhaust + " 2", ValveKind.EXHAUST, 2));
        } else {
            rows.add(new ValveRow(exhaust, ValveKind.EXHAUST, 1));
        }
        return rows;
    }

    private record ValveRow(String name, ValveKind kind, int index) {
    }

    /** A check point and its target or limit (empty if none is stored). */
    private record CheckRow(String label, String target) {
    }

    private void writeBrakes(PdfPageWriter writer, Values values, PdfTexts texts) throws IOException {
        VehicleSpecification spec = values.spec();
        writeCheckTable(writer, texts.get("worksheet.front"), List.of(
                new CheckRow(texts.get("worksheet.brakes.padThickness"), values.min(spec.getBrakePadMinFront())),
                new CheckRow(texts.get("worksheet.brakes.discThickness"), values.min(spec.getBrakeDiscMinFront())),
                new CheckRow(texts.get("worksheet.brakes.discCondition"), "")), texts);
        writeCheckTable(writer, texts.get("worksheet.rear"), List.of(
                new CheckRow(texts.get("worksheet.brakes.padThickness"), values.min(spec.getBrakePadMinRear())),
                new CheckRow(texts.get("worksheet.brakes.discThickness"), values.min(spec.getBrakeDiscMinRear())),
                new CheckRow(texts.get("worksheet.brakes.discCondition"), "")), texts);
        writeCheckTable(writer, texts.get("worksheet.brakes.fluid"), List.of(
                new CheckRow(texts.get("worksheet.brakes.fluidLevel"), ""),
                new CheckRow(texts.get("worksheet.brakes.fluidColour"), ""),
                new CheckRow(texts.get("worksheet.brakes.fluidLastChanged"), "")), texts);
    }

    private void writeChain(PdfPageWriter writer, Values values, PdfTexts texts) throws IOException {
        VehicleSpecification spec = values.spec();
        String wearLabel = spec.getChainWearLinks() == null ? texts.get("worksheet.chain.wear")
                : texts.get("worksheet.chain.wearLinks", texts.number(spec.getChainWearLinks()));
        writeCheckTable(writer, texts.get("worksheet.chain.measurement"), List.of(
                new CheckRow(texts.get("worksheet.chain.slack"),
                        values.range(spec.getChainSlackMin(), spec.getChainSlackMax())),
                new CheckRow(wearLabel, values.max(spec.getChainWearMaxLength()))), texts);
        writeCheckTable(writer, texts.get("worksheet.chain.condition"), List.of(
                new CheckRow(texts.get("worksheet.chain.sprocket"), ""),
                new CheckRow(texts.get("worksheet.chain.rearSprocket"), "")), texts);
        writer.writeCheckboxItem(texts.get("worksheet.chain.cleaned"), FONT_TEXT, 11f);
        writer.writeCheckboxItem(texts.get("worksheet.chain.lubricated"), FONT_TEXT, 11f);
        writer.newLine();
    }

    private void writeTires(PdfPageWriter writer, Values values, PdfTexts texts) throws IOException {
        VehicleSpecification spec = values.spec();
        writeCheckTable(writer, texts.get("worksheet.front"), tireRows(values.bar(spec.getTirePressureFront()),
                values.min(spec.getTreadDepthMin()), texts), texts);
        writeCheckTable(writer, texts.get("worksheet.rear"), tireRows(values.bar(spec.getTirePressureRear()),
                values.min(spec.getTreadDepthMin()), texts), texts);
    }

    private static List<CheckRow> tireRows(String pressure, String tread, PdfTexts texts) {
        return List.of(
                new CheckRow(texts.get("worksheet.tires.pressure"), pressure),
                new CheckRow(texts.get("worksheet.tires.tread"), tread),
                new CheckRow(texts.get("worksheet.tires.dot"), ""),
                new CheckRow(texts.get("worksheet.tires.condition"), ""));
    }

    /** Heading and a table with one row per check point; kept together on one page. */
    private void writeCheckTable(PdfPageWriter writer, String heading, List<CheckRow> rows, PdfTexts texts)
            throws IOException {
        writer.ensureSpace(blockHeight(rows.size()));
        writer.writeLine(heading, FONT_HEADING, 12f);
        writer.writeTableRow(List.of(
                PdfCell.of(texts.get("worksheet.column.checkpoint")),
                PdfCell.of(texts.get("worksheet.column.target")),
                PdfCell.of(texts.get("worksheet.column.actual")),
                PdfCell.of(texts.get("worksheet.column.ok"))), CHECK_COLUMNS, FONT_HEADING, TABLE_FONT_SIZE, true);
        for (CheckRow row : rows) {
            writer.writeTableRow(List.of(PdfCell.of(row.label()), PdfCell.of(row.target()), PdfCell.blank(),
                    PdfCell.checkbox()), CHECK_COLUMNS, FONT_TEXT, TABLE_FONT_SIZE, false);
        }
        writer.newLine();
    }

    private void writeFillInFields(PdfPageWriter writer, PdfTexts texts) throws IOException {
        writer.newLine();
        // Date, mileage, done by and notes plus the empty note lines stay together on one page.
        writer.ensureSpace((4 + NOTE_LINES) * PdfPageWriter.fillInLineHeight());
        writer.writeFillInLine(texts.get("checklist.fillIn.date"), FONT_TEXT, 11f);
        writer.writeFillInLine(texts.get("checklist.fillIn.mileage"), FONT_TEXT, 11f);
        writer.writeFillInLine(texts.get("worksheet.fillIn.doneBy"), FONT_TEXT, 11f);
        writer.writeFillInLine(texts.get("checklist.fillIn.notes"), FONT_TEXT, 11f);
        for (int i = 0; i < NOTE_LINES; i++) {
            writer.writeFillInLine(null, FONT_TEXT, 11f);
        }
    }

    /** Heading line plus header row plus {@code rows} rows. */
    private static float blockHeight(int rows) {
        return 16f + (rows + 1) * PdfPageWriter.tableRowHeight();
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
            return mm(min) + "\u2013" + mm(max);
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
