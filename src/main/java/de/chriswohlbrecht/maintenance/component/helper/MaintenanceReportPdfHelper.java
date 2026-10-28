package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/** Renders the maintenance history of a vehicle as PDF, with all labels, dates and numbers in the given locale. */
@Component
@RequiredArgsConstructor
public class MaintenanceReportPdfHelper {

    private static final float MARGIN = 50f;
    private static final float LEADING = 16f;
    private static final PDFont FONT_TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_HEADING = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_TEXT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private final MessageSource messageSource;

    /**
     * @param locale locale of the vehicle owner from {@code IUserLocaleComponent#localeOf} (de-DE or en-GB). Other
     *               locales are not supported: e.g. French number formats use U+202F, which the PDF font lacks.
     */
    public byte[] generate(Vehicle vehicle, List<MaintenanceReportEntry> entries, Locale locale) {
        try (PDDocument document = new PDDocument()) {
            ReportWriter writer = new ReportWriter(document);
            Texts texts = new Texts(messageSource, locale);
            writeHeader(writer, vehicle, texts);
            writeEntries(writer, entries, texts);
            writer.close();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate maintenance report PDF", e);
        }
    }

    private void writeHeader(ReportWriter writer, Vehicle vehicle, Texts texts) throws IOException {
        writer.writeLine(texts.get("report.title"), FONT_TITLE, 18f);
        writer.writeLine(vehicle.getName(), FONT_HEADING, 13f);
        writer.newLine();

        writer.writeLine(texts.get("report.type", texts.get("vehicleType." + vehicle.getType().name())), FONT_TEXT,
                11f);
        if (vehicle.getMake() != null || vehicle.getModel() != null) {
            String makeModel = String.join(" ",
                    vehicle.getMake() == null ? "" : vehicle.getMake(),
                    vehicle.getModel() == null ? "" : vehicle.getModel()).trim();
            writer.writeLine(texts.get("report.makeModel", makeModel), FONT_TEXT, 11f);
        }
        if (vehicle.getModelYear() != null) {
            // No thousands separator in a year.
            writer.writeLine(texts.get("report.modelYear", String.valueOf(vehicle.getModelYear())), FONT_TEXT, 11f);
        }
        writer.writeLine(texts.get("report.currentMileage", texts.kilometers(vehicle.getCurrentMileage())), FONT_TEXT,
                11f);
        writer.newLine();
    }

    private void writeEntries(ReportWriter writer, List<MaintenanceReportEntry> entries, Texts texts)
            throws IOException {
        writer.writeLine(texts.get("report.performedMaintenance"), FONT_HEADING, 13f);
        writer.newLine();

        if (entries.isEmpty()) {
            writer.writeLine(texts.get("report.noEntries"), FONT_TEXT, 11f);
            return;
        }

        for (MaintenanceReportEntry entry : entries) {
            writer.writeLine(texts.get("report.entry", texts.date(entry), texts.kilometers(entry.mileageAtPerformed())),
                    FONT_HEADING, 11f);
            if (!entry.performedTaskNames().isEmpty()) {
                writer.writeLine(texts.get("report.tasks", String.join(", ", entry.performedTaskNames())), FONT_TEXT,
                        11f);
            }
            if (entry.notes() != null && !entry.notes().isBlank()) {
                writer.writeLine(texts.get("report.notes", entry.notes()), FONT_TEXT, 11f);
            }
            writer.newLine();
        }
    }

    /**
     * Translated texts and locale-specific formats. Arguments are passed as preformatted strings so that
     * MessageFormat does not apply its own number formatting.
     */
    private static final class Texts {
        private final MessageSource messageSource;
        private final Locale locale;
        private final DateTimeFormatter dateFormatter;
        private final NumberFormat numberFormat;

        private Texts(MessageSource messageSource, Locale locale) {
            this.messageSource = messageSource;
            this.locale = locale;
            // de-DE: 09.10.2026, en-GB: 9 Oct 2026 – like the frontend's mediumDate.
            this.dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
            this.numberFormat = NumberFormat.getIntegerInstance(locale);
        }

        private String get(String key, String... args) {
            return messageSource.getMessage(key, args, locale);
        }

        private String date(MaintenanceReportEntry entry) {
            return entry.performedAt().format(dateFormatter);
        }

        private String kilometers(Integer value) {
            return value == null ? "" : numberFormat.format(value);
        }
    }

    private static final class ReportWriter {
        private final PDDocument document;
        private PDPageContentStream contentStream;
        private float yPosition;

        private ReportWriter(PDDocument document) throws IOException {
            this.document = document;
            startNewPage();
        }

        private void startNewPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            yPosition = page.getMediaBox().getHeight() - MARGIN;
        }

        private void writeLine(String text, PDFont font, float fontSize) throws IOException {
            if (yPosition < MARGIN) {
                startNewPage();
            }
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(MARGIN, yPosition);
            contentStream.showText(text == null ? "" : text);
            contentStream.endText();
            yPosition -= LEADING;
        }

        private void newLine() {
            yPosition -= LEADING / 2;
        }

        private void close() throws IOException {
            contentStream.close();
        }
    }
}
