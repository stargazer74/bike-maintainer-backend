package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders an empty worksheet to fill in by hand in the workshop: vehicle at the top, the measuring tables of the
 * template, and fields for date, mileage, who did the work and notes at the bottom. Nothing is prefilled except the
 * vehicle.
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
     * @param layout cylinders and valves, only for {@link WorksheetTemplate#VALVE_CLEARANCE} (ignored otherwise)
     * @param locale de-DE or en-GB, see {@code MaintenanceReportPdfHelper#generate}
     */
    public byte[] generate(Vehicle vehicle, WorksheetTemplate template, ValveLayout layout, LocalDate printDate,
                           Locale locale) {
        try (PDDocument document = new PDDocument()) {
            PdfTexts texts = new PdfTexts(messageSource, locale);
            String titleKey = "worksheet.title." + template.name();
            PdfPageWriter writer = new PdfPageWriter(document, texts.frame(titleKey, vehicle, printDate));
            PdfVehicleHeader.write(writer, texts.get(titleKey), vehicle, printDate, texts);
            switch (template) {
                case VALVE_CLEARANCE -> writeValveClearance(writer, layout, texts);
                case BRAKES -> writeBrakes(writer, texts);
                case CHAIN -> writeChain(writer, texts);
                case TIRES -> writeTires(writer, texts);
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

    private void writeValveClearance(PdfPageWriter writer, ValveLayout layout, PdfTexts texts) throws IOException {
        writer.writeCheckboxItem(texts.get("worksheet.valve.engineCold"), FONT_TEXT, 11f);
        writer.writeCheckboxItem(texts.get("worksheet.valve.tdcChecked"), FONT_TEXT, 11f);
        writer.newLine();
        List<String> valves = valveNames(layout.valvesPerCylinder(), texts);
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
            for (String valve : valves) {
                writer.writeTableRow(List.of(PdfCell.of(valve), PdfCell.of("–"), PdfCell.blank(),
                        PdfCell.blank(), PdfCell.blank(), PdfCell.checkbox()), VALVE_COLUMNS, FONT_TEXT, TABLE_FONT_SIZE,
                        false);
            }
            writer.newLine();
        }
    }

    /** 2 valves: intake, exhaust; 3: two intakes, one exhaust; 4: two of each. */
    private static List<String> valveNames(int valvesPerCylinder, PdfTexts texts) {
        String intake = texts.get("worksheet.valve.intake");
        String exhaust = texts.get("worksheet.valve.exhaust");
        List<String> names = new ArrayList<>();
        if (valvesPerCylinder == 2) {
            names.add(intake);
        } else {
            names.add(intake + " 1");
            names.add(intake + " 2");
        }
        if (valvesPerCylinder == 4) {
            names.add(exhaust + " 1");
            names.add(exhaust + " 2");
        } else {
            names.add(exhaust);
        }
        return names;
    }

    private void writeBrakes(PdfPageWriter writer, PdfTexts texts) throws IOException {
        List<String> perWheel = List.of("worksheet.brakes.padThickness", "worksheet.brakes.discThickness",
                "worksheet.brakes.discCondition");
        writeCheckTable(writer, texts.get("worksheet.front"), perWheel, texts);
        writeCheckTable(writer, texts.get("worksheet.rear"), perWheel, texts);
        writeCheckTable(writer, texts.get("worksheet.brakes.fluid"), List.of("worksheet.brakes.fluidLevel",
                "worksheet.brakes.fluidColour", "worksheet.brakes.fluidLastChanged"), texts);
    }

    private void writeChain(PdfPageWriter writer, PdfTexts texts) throws IOException {
        writeCheckTable(writer, texts.get("worksheet.chain.measurement"), List.of("worksheet.chain.slack",
                "worksheet.chain.wear"), texts);
        writeCheckTable(writer, texts.get("worksheet.chain.condition"), List.of("worksheet.chain.sprocket",
                "worksheet.chain.rearSprocket"), texts);
        writer.writeCheckboxItem(texts.get("worksheet.chain.cleaned"), FONT_TEXT, 11f);
        writer.writeCheckboxItem(texts.get("worksheet.chain.lubricated"), FONT_TEXT, 11f);
        writer.newLine();
    }

    private void writeTires(PdfPageWriter writer, PdfTexts texts) throws IOException {
        List<String> perWheel = List.of("worksheet.tires.pressure", "worksheet.tires.tread", "worksheet.tires.dot",
                "worksheet.tires.condition");
        writeCheckTable(writer, texts.get("worksheet.front"), perWheel, texts);
        writeCheckTable(writer, texts.get("worksheet.rear"), perWheel, texts);
    }

    /** Heading and a table with one row per check point; kept together on one page. */
    private void writeCheckTable(PdfPageWriter writer, String heading, List<String> rowKeys, PdfTexts texts)
            throws IOException {
        writer.ensureSpace(blockHeight(rowKeys.size()));
        writer.writeLine(heading, FONT_HEADING, 12f);
        writer.writeTableRow(List.of(
                PdfCell.of(texts.get("worksheet.column.checkpoint")),
                PdfCell.of(texts.get("worksheet.column.target")),
                PdfCell.of(texts.get("worksheet.column.actual")),
                PdfCell.of(texts.get("worksheet.column.ok"))), CHECK_COLUMNS, FONT_HEADING, TABLE_FONT_SIZE, true);
        for (String key : rowKeys) {
            writer.writeTableRow(List.of(PdfCell.of(texts.get(key)), PdfCell.blank(), PdfCell.blank(),
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
}
