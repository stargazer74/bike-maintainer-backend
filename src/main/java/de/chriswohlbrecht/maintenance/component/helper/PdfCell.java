package de.chriswohlbrecht.maintenance.component.helper;

/**
 * One cell of a table row in a PDF form: a text (may be empty, to fill in by hand) or an empty box to tick.
 */
record PdfCell(String text, boolean box) {

    static PdfCell of(String text) {
        return new PdfCell(text, false);
    }

    static PdfCell blank() {
        return new PdfCell("", false);
    }

    static PdfCell checkbox() {
        return new PdfCell("", true);
    }
}
