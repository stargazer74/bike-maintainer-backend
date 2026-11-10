package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * The vehicle block at the top of the printable forms (checklist, worksheets) and their file names, so all of them
 * look and are named alike.
 */
final class PdfVehicleHeader {

    private static final PdfFont FONT_TITLE = PdfFont.TITLE;
    private static final PdfFont FONT_HEADING = PdfFont.HEADING;
    private static final PdfFont FONT_TEXT = PdfFont.TEXT;
    private static final int MAX_FILE_NAME_PART = 40;

    private PdfVehicleHeader() {
    }

    /** Title, vehicle name, make/model, model year, current mileage and print date. */
    static void write(PdfPageWriter writer, String title, Vehicle vehicle, LocalDate printDate, PdfTexts texts)
            throws IOException {
        writer.writeLine(title, FONT_TITLE, 18f);
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

    /** E.g. {@code checkliste-die-rote-2026-10-29.pdf}: only lower-case ASCII letters, digits and dashes. */
    static String fileName(String prefix, Vehicle vehicle, LocalDate printDate) {
        String vehiclePart = slug(vehicle.getName());
        return prefix + (vehiclePart.isEmpty() ? "" : "-" + vehiclePart) + "-" + printDate + ".pdf";
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
}
