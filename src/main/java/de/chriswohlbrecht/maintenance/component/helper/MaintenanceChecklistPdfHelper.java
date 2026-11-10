package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Renders a printable checklist of maintenance tasks: one box to tick per task with its due status, and fields to
 * fill in by hand at the bottom. The entries are printed in the given order (the caller sorts them by urgency).
 */
@Component
@RequiredArgsConstructor
public class MaintenanceChecklistPdfHelper {

    private static final PdfFont FONT_HEADING = PdfFont.HEADING;
    private static final PdfFont FONT_TEXT = PdfFont.TEXT;
    private static final int NOTE_LINES = 4;

    private final MessageSource messageSource;

    /** @param locale de-DE or en-GB, see {@code MaintenanceReportPdfHelper#generate}. */
    public byte[] generate(Vehicle vehicle, List<MaintenanceChecklistEntry> entries, LocalDate printDate,
                           Locale locale) {
        try (PDDocument document = new PDDocument()) {
            PdfTexts texts = new PdfTexts(messageSource, locale);
            PdfPageWriter writer = new PdfPageWriter(document, texts.frame("checklist.title", vehicle, printDate));
            PdfVehicleHeader.write(writer, texts.get("checklist.title"), vehicle, printDate, texts);
            for (MaintenanceChecklistEntry entry : entries) {
                writeEntry(writer, entry, texts);
            }
            writeFillInFields(writer, texts);
            writer.close();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate maintenance checklist PDF", e);
        }
    }

    /** E.g. {@code checkliste-die-rote-2026-10-29.pdf}: only lower-case ASCII letters, digits and dashes. */
    public String fileName(Vehicle vehicle, LocalDate printDate, Locale locale) {
        return PdfVehicleHeader.fileName(messageSource.getMessage("checklist.fileName", null, locale), vehicle,
                printDate);
    }

    private void writeEntry(PdfPageWriter writer, MaintenanceChecklistEntry entry, PdfTexts texts) throws IOException {
        float indent = PdfPageWriter.checkboxIndent();
        // Keep the box together with at least the first lines of its task.
        writer.ensureSpace(64f);
        writer.writeCheckboxItem(entry.name(), FONT_HEADING, 12f);
        if (entry.description() != null && !entry.description().isBlank()) {
            writer.writeWrapped(entry.description(), FONT_TEXT, 10f, indent);
        }
        boolean overdue = entry.status().status() == MaintenanceTaskStatus.OVERDUE;
        writer.writeWrapped(due(entry, texts), overdue ? FONT_HEADING : FONT_TEXT, 10f, indent);
        writer.writeWrapped(lastService(entry, texts) + " · " + interval(entry, texts), FONT_TEXT, 10f, indent);
        writer.newLine();
    }

    private void writeFillInFields(PdfPageWriter writer, PdfTexts texts) throws IOException {
        writer.newLine();
        // Date, mileage and notes plus the empty note lines stay together on one page.
        writer.ensureSpace((3 + NOTE_LINES) * PdfPageWriter.fillInLineHeight());
        writer.writeFillInLine(texts.get("checklist.fillIn.date"), FONT_TEXT, 11f);
        writer.writeFillInLine(texts.get("checklist.fillIn.mileage"), FONT_TEXT, 11f);
        writer.writeFillInLine(texts.get("checklist.fillIn.notes"), FONT_TEXT, 11f);
        for (int i = 0; i < NOTE_LINES; i++) {
            writer.writeFillInLine(null, FONT_TEXT, 11f);
        }
    }

    private static String due(MaintenanceChecklistEntry entry, PdfTexts texts) {
        MaintenanceTaskStatusResult status = entry.status();
        if (status.kmRemaining() != null) {
            return remaining(status.kmRemaining(), "Km", texts);
        }
        if (status.daysRemaining() != null) {
            return remaining(status.daysRemaining(), "Days", texts);
        }
        return texts.get(status.lastServiceDate() == null ? "checklist.neverPerformed" : "checklist.noDueDate");
    }

    /** Signed remaining value: positive = due in, negative = overdue by, zero = due now. */
    private static String remaining(int value, String unit, PdfTexts texts) {
        if (value == 0) {
            return texts.get("checklist.dueNow");
        }
        String kind = value > 0 ? "checklist.dueIn" : "checklist.overdueBy";
        int amount = Math.abs(value);
        String suffix = "Days".equals(unit) && amount == 1 ? "Day" : unit;
        return texts.get(kind + suffix, texts.number(amount));
    }

    private static String lastService(MaintenanceChecklistEntry entry, PdfTexts texts) {
        MaintenanceTaskStatusResult status = entry.status();
        if (status.lastServiceDate() == null) {
            return texts.get("checklist.lastNever");
        }
        if (status.lastServiceMileage() == null) {
            return texts.get("checklist.lastDate", texts.date(status.lastServiceDate()));
        }
        return texts.get("checklist.last", texts.date(status.lastServiceDate()),
                texts.number(status.lastServiceMileage()));
    }

    private static String interval(MaintenanceChecklistEntry entry, PdfTexts texts) {
        if (entry.oneTime()) {
            return texts.get("checklist.interval.oneTime");
        }
        boolean km = entry.intervalKm() != null && entry.intervalKm() > 0;
        boolean months = entry.intervalMonths() != null && entry.intervalMonths() > 0;
        if (km && months) {
            return texts.get("checklist.interval.kmOrMonths", texts.number(entry.intervalKm()),
                    texts.number(entry.intervalMonths()));
        }
        if (km) {
            return texts.get("checklist.interval.km", texts.number(entry.intervalKm()));
        }
        if (months) {
            return texts.get(entry.intervalMonths() == 1 ? "checklist.interval.month" : "checklist.interval.months",
                    texts.number(entry.intervalMonths()));
        }
        return texts.get("checklist.interval.none");
    }
}
