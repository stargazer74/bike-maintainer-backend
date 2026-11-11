package de.chriswohlbrecht.maintenance.component.helper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/** File names of everything downloaded for a vehicle (forms, export), so all of them are named alike. */
public final class VehicleFileNames {

    private static final int MAX_FILE_NAME_PART = 40;

    private VehicleFileNames() {
    }

    /**
     * E.g. {@code vehicle-die-rote-2026-11-11.json}: only lower-case ASCII letters, digits and dashes; the vehicle
     * part is left out if nothing of the name is left.
     */
    public static String fileName(String prefix, String vehicleName, LocalDate date, String extension) {
        String vehiclePart = slug(vehicleName);
        return prefix + (vehiclePart.isEmpty() ? "" : "-" + vehiclePart) + "-" + date + "." + extension;
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
