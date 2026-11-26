package de.chriswohlbrecht.maintenance.component.helper;

import java.util.Locale;

/** Spelling-tolerant comparison of makes and models for the template search. */
public final class VehicleTemplateNames {

    private VehicleTemplateNames() {
    }

    /** Lower case without spaces, hyphens, underscores and dots, so "Moto-Guzzi" equals "moto guzzi". */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (!Character.isWhitespace(c) && c != '-' && c != '_' && c != '.') {
                result.append(c);
            }
        }
        return result.toString();
    }
}
