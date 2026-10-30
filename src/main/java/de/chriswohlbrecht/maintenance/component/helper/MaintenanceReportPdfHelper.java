package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Renders the maintenance history of a vehicle as PDF, with all labels, dates and numbers in the given locale. */
@Component
@RequiredArgsConstructor
public class MaintenanceReportPdfHelper {

    private static final PdfFont FONT_TITLE = PdfFont.TITLE;
    private static final PdfFont FONT_HEADING = PdfFont.HEADING;
    private static final PdfFont FONT_TEXT = PdfFont.TEXT;

    private final MessageSource messageSource;

    /**
     * @param locale locale of the vehicle owner from {@code IUserLocaleComponent#localeOf} (de-DE or en-GB); the
     *               texts exist only in these two languages.
     * @param createdOn date in the footer of every page
     */
    public byte[] generate(Vehicle vehicle, List<MaintenanceReportEntry> entries, LocalDate createdOn,
                           Locale locale) {
        try (PDDocument document = new PDDocument()) {
            Texts texts = new Texts(messageSource, locale);
            PdfPageWriter writer = new PdfPageWriter(document, new PdfFrame(texts.get("report.title"),
                    vehicle.getName(), texts.get("pdf.footer.created", createdOn.format(texts.dateFormatter)),
                    (page, total) -> texts.get("pdf.footer.page", String.valueOf(page), String.valueOf(total))));
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

    private void writeHeader(PdfPageWriter writer, Vehicle vehicle, Texts texts) throws IOException {
        writer.writeLine(texts.get("report.title"), FONT_TITLE, 18f);
        writer.writeWrapped(vehicle.getName(), FONT_HEADING, 13f, 0f);
        writer.newLine();

        writer.writeLine(texts.get("report.type", texts.get("vehicleType." + vehicle.getType().name())), FONT_TEXT,
                11f);
        if (vehicle.getMake() != null || vehicle.getModel() != null) {
            String makeModel = String.join(" ",
                    vehicle.getMake() == null ? "" : vehicle.getMake(),
                    vehicle.getModel() == null ? "" : vehicle.getModel()).trim();
            writer.writeWrapped(texts.get("report.makeModel", makeModel), FONT_TEXT, 11f, 0f);
        }
        if (vehicle.getModelYear() != null) {
            // No thousands separator in a year.
            writer.writeLine(texts.get("report.modelYear", String.valueOf(vehicle.getModelYear())), FONT_TEXT, 11f);
        }
        writer.writeLine(texts.get("report.currentMileage", texts.kilometers(vehicle.getCurrentMileage())), FONT_TEXT,
                11f);
        writer.newLine();
    }

    private void writeEntries(PdfPageWriter writer, List<MaintenanceReportEntry> entries, Texts texts)
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
                writer.writeWrapped(texts.get("report.tasks", String.join(", ", entry.performedTaskNames())),
                        FONT_TEXT, 11f, 0f);
            }
            if (entry.notes() != null && !entry.notes().isBlank()) {
                writer.writeWrapped(texts.get("report.notes", entry.notes()), FONT_TEXT, 11f, 0f);
            }
            writeCosts(writer, entry, texts);
            writer.newLine();
        }
        writeGrandTotals(writer, entries, texts);
    }

    private void writeCosts(PdfPageWriter writer, MaintenanceReportEntry entry, Texts texts) throws IOException {
        if (!entry.materials().isEmpty()) {
            writer.writeLine(texts.get("report.materials"), FONT_TEXT, 11f);
            for (ReportMaterial material : entry.materials()) {
                writer.writeWrapped("• " + texts.material(material, entry.currency()), FONT_TEXT, 11f, 8f);
            }
        }
        if (entry.laborCost() != null) {
            writer.writeLine(texts.get("report.laborCost", texts.money(entry.laborCost(), entry.currency())),
                    FONT_TEXT, 11f);
        }
        if (entry.totalCost() != null) {
            writer.writeLine(texts.get("report.total", texts.money(entry.totalCost(), entry.currency())),
                    FONT_HEADING, 11f);
        }
    }

    /** One line per currency, sorted by code; nothing if no entry has costs. */
    private void writeGrandTotals(PdfPageWriter writer, List<MaintenanceReportEntry> entries, Texts texts)
            throws IOException {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (MaintenanceReportEntry entry : entries) {
            if (entry.totalCost() != null) {
                totals.merge(entry.currency(), entry.totalCost(), BigDecimal::add);
            }
        }
        if (totals.isEmpty()) {
            return;
        }
        writer.writeLine(texts.get("report.grandTotal"), FONT_HEADING, 13f);
        for (Map.Entry<String, BigDecimal> total : totals.entrySet()) {
            writer.writeLine(texts.money(total.getValue(), total.getKey()), FONT_TEXT, 11f);
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

        private String money(BigDecimal amount, String currencyCode) {
            NumberFormat format = NumberFormat.getCurrencyInstance(locale);
            format.setCurrency(java.util.Currency.getInstance(currencyCode));
            return format.format(amount);
        }

        /** Name, part number, quantity with unit and price, as far as present. */
        private String material(ReportMaterial material, String currencyCode) {
            List<String> parts = new ArrayList<>();
            parts.add(material.name());
            if (material.partNumber() != null && !material.partNumber().isBlank()) {
                parts.add(get("report.material.partNumber", material.partNumber()));
            }
            NumberFormat quantityFormat = NumberFormat.getNumberInstance(locale);
            quantityFormat.setMaximumFractionDigits(3);
            parts.add(quantityFormat.format(material.quantity()) + " " + get("unit." + material.unit().name()));
            if (material.price() != null) {
                parts.add(money(material.price(), currencyCode));
            }
            return String.join(" – ", parts);
        }
    }
}
