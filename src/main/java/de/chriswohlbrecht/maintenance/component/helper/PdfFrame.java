package de.chriswohlbrecht.maintenance.component.helper;

/**
 * What the header and footer of every page show: document type and vehicle at the top right, the creation note
 * and the page number at the bottom. All texts are already translated.
 *
 * @param documentType e.g. "Wartungshistorie"
 * @param subject      e.g. the vehicle name; shortened if it does not fit
 * @param footerText   e.g. "Erstellt mit Bike Maintainer am 30.10.2026"
 * @param pageLabel    e.g. "Seite 1 von 3"
 */
record PdfFrame(String documentType, String subject, String footerText, PageLabel pageLabel) {

    @FunctionalInterface
    interface PageLabel {
        String of(int page, int totalPages);
    }
}
