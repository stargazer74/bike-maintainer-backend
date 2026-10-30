package de.chriswohlbrecht.maintenance.component.helper;

/** Text styles of the PDF documents; each maps to a cut of the embedded Inter font. */
enum PdfFont {
    TITLE("fonts/Inter-Bold.ttf"),
    HEADING("fonts/Inter-SemiBold.ttf"),
    TEXT("fonts/Inter-Regular.ttf");

    private final String resource;

    PdfFont(String resource) {
        this.resource = resource;
    }

    String resource() {
        return resource;
    }
}
