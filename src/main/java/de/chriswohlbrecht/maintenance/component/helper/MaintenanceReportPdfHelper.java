package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MaintenanceReportPdfHelper {

    private static final float MARGIN = 50f;
    private static final float LEADING = 16f;
    private static final PDFont FONT_TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_HEADING = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_TEXT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] generate(Vehicle vehicle, List<MaintenanceReportEntry> entries) {
        try (PDDocument document = new PDDocument()) {
            ReportWriter writer = new ReportWriter(document);
            writeHeader(writer, vehicle);
            writeEntries(writer, entries);
            writer.close();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate maintenance report PDF", e);
        }
    }

    private void writeHeader(ReportWriter writer, Vehicle vehicle) throws IOException {
        writer.writeLine("Wartungshistorie", FONT_TITLE, 18f);
        writer.writeLine(vehicle.getName(), FONT_HEADING, 13f);
        writer.newLine();

        writer.writeLine("Typ: " + vehicle.getType(), FONT_TEXT, 11f);
        if (vehicle.getMake() != null || vehicle.getModel() != null) {
            String makeModel = String.join(" ",
                    vehicle.getMake() == null ? "" : vehicle.getMake(),
                    vehicle.getModel() == null ? "" : vehicle.getModel()).trim();
            writer.writeLine("Marke/Modell: " + makeModel, FONT_TEXT, 11f);
        }
        if (vehicle.getModelYear() != null) {
            writer.writeLine("Baujahr: " + vehicle.getModelYear(), FONT_TEXT, 11f);
        }
        writer.writeLine("Aktueller Kilometerstand: " + vehicle.getCurrentMileage() + " km", FONT_TEXT, 11f);
        writer.newLine();
    }

    private void writeEntries(ReportWriter writer, List<MaintenanceReportEntry> entries) throws IOException {
        writer.writeLine("Durchgeführte Wartungen", FONT_HEADING, 13f);
        writer.newLine();

        if (entries.isEmpty()) {
            writer.writeLine("Keine Wartungen erfasst.", FONT_TEXT, 11f);
            return;
        }

        for (MaintenanceReportEntry entry : entries) {
            writer.writeLine(entry.performedAt().format(DATE_FORMATTER) + " – " + entry.mileageAtPerformed() + " km",
                    FONT_HEADING, 11f);
            if (!entry.performedTaskNames().isEmpty()) {
                writer.writeLine("Aufgaben: " + String.join(", ", entry.performedTaskNames()), FONT_TEXT, 11f);
            }
            if (entry.notes() != null && !entry.notes().isBlank()) {
                writer.writeLine("Notizen: " + entry.notes(), FONT_TEXT, 11f);
            }
            writer.newLine();
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
