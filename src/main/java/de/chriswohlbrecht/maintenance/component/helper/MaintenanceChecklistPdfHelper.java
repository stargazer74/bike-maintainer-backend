package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * Renders a printable checklist of maintenance tasks: one box to tick per task with its due status, and fields to
 * fill in by hand at the bottom. The entries are printed in the given order (the caller sorts them by urgency).
 */
@Component
@RequiredArgsConstructor
public class MaintenanceChecklistPdfHelper {

    private static final PDFont FONT_TITLE = PdfPageWriter.FONT_TITLE;
    private static final PDFont FONT_HEADING = PdfPageWriter.FONT_HEADING;
    private static final PDFont FONT_TEXT = PdfPageWriter.FONT_TEXT;
    private static final int MAX_FILE_NAME_PART = 40;
    private static final int NOTE_LINES = 4;

    private final MessageSource messageSource;

    /** @param locale de-DE or en-GB, see {@code MaintenanceReportPdfHelper#generate}. */
    public byte[] generate(Vehicle vehicle, List<MaintenanceChecklistEntry> entries, LocalDate printDate,
                           Locale locale) {
        try (PDDocument document = new PDDocument()) {
            PdfPageWriter writer = new PdfPageWriter(document);
            Texts texts = new Texts(messageSource, locale);
            writeHeader(writer, vehicle, printDate, texts);
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
        String prefix = messageSource.getMessage("checklist.fileName", null, locale);
        String vehiclePart = slug(vehicle.getName());
        return prefix + (vehiclePart.isEmpty() ? "" : "-" + vehiclePart) + "-" + printDate + ".pdf";
    }

    private void writeHeader(PdfPageWriter writer, Vehicle vehicle, LocalDate printDate, Texts texts)
            throws IOException {
        writer.writeLine(texts.get("checklist.title"), FONT_TITLE, 18f);
        writer.writeWrapped(vehicle.getName(), FONT_HEADING, 13f, 0f);
        writer.newLine();
        if (vehicle.getMake() != null || vehicle.getModel() != null) {
            String makeModel = String.join(" ",
                    vehicle.getMake() == null ? "" : vehicle.getMake(),
                    vehicle.getModel() == null ? "" : vehicle.getModel()).trim();
            writer.writeWrapped(texts.get("report.makeModel", makeModel), FONT_TEXT, 11f, 0f);
        }
        if (vehicle.getModelYear() != null) {
            writer.writeLine(texts.get("report.modelYear", String.valueOf(vehicle.getModelYear())), FONT_TEXT, 11f);
        }
        writer.writeLine(texts.get("report.currentMileage", texts.number(vehicle.getCurrentMileage())), FONT_TEXT,
                11f);
        writer.writeLine(texts.get("checklist.printed", texts.date(printDate)), FONT_TEXT, 11f);
        writer.newLine();
        writer.newLine();
    }

    private void writeEntry(PdfPageWriter writer, MaintenanceChecklistEntry entry, Texts texts) throws IOException {
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

    private void writeFillInFields(PdfPageWriter writer, Texts texts) throws IOException {
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

    private static String due(MaintenanceChecklistEntry entry, Texts texts) {
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
    private static String remaining(int value, String unit, Texts texts) {
        if (value == 0) {
            return texts.get("checklist.dueNow");
        }
        String kind = value > 0 ? "checklist.dueIn" : "checklist.overdueBy";
        int amount = Math.abs(value);
        String suffix = "Days".equals(unit) && amount == 1 ? "Day" : unit;
        return texts.get(kind + suffix, texts.number(amount));
    }

    private static String lastService(MaintenanceChecklistEntry entry, Texts texts) {
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

    private static String interval(MaintenanceChecklistEntry entry, Texts texts) {
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

    private static String slug(String name) {
        if (name == null) {
            return "";
        }
        String transliterated = name.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        String ascii = Normalizer.normalize(transliterated, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_FILE_NAME_PART) {
            slug = slug.substring(0, MAX_FILE_NAME_PART).replaceAll("-+$", "");
        }
        return slug;
    }

    /** Translated texts; arguments are preformatted strings so MessageFormat applies no own number format. */
    private static final class Texts {
        private final MessageSource messageSource;
        private final Locale locale;
        private final DateTimeFormatter dateFormatter;
        private final NumberFormat numberFormat;

        private Texts(MessageSource messageSource, Locale locale) {
            this.messageSource = messageSource;
            this.locale = locale;
            this.dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
            this.numberFormat = NumberFormat.getIntegerInstance(locale);
        }

        private String get(String key, String... args) {
            return messageSource.getMessage(key, args, locale);
        }

        private String date(LocalDate date) {
            return date.format(dateFormatter);
        }

        private String number(Integer value) {
            return value == null ? "" : numberFormat.format(value);
        }
    }
}
