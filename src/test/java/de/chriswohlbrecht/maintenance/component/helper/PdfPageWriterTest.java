package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPageWriterTest {

    @Test
    void replacesCharactersTheFontCannotShow() throws IOException {
        String text = render(writer -> writer.writeLine("Kette 🔧 → prüfen 12 €", PdfPageWriter.FONT_TEXT, 11f));

        assertThat(text).contains("Kette ? ? prüfen 12 €");
    }

    @Test
    void showsDecomposedUmlautsAndDropsInvisibleCharacters() throws IOException {
        String text = render(writer -> writer.writeLine(
                "Mu\u0308ller Brems\u00ADbeläge Ket\u200Bte \u2764\uFE0F", PdfPageWriter.FONT_TEXT, 11f));

        assertThat(text).contains("Müller Bremsbeläge Kette ?").doesNotContain("??");
    }

    @Test
    void wrapsLongTextAtWordBoundaries() throws IOException {
        String longText = "Bremsbeläge vorne und hinten prüfen, Bremsflüssigkeit wechseln, Bremsscheiben auf "
                + "Riefen und Mindeststärke kontrollieren, Bremssättel reinigen und Kolben gangbar machen.";

        String text = render(writer -> writer.writeWrapped(longText, PdfPageWriter.FONT_TEXT, 11f, 0f));

        String[] lines = text.strip().split("\\R");
        assertThat(lines.length).isGreaterThan(1);
        assertThat(String.join(" ", lines)).isEqualTo(longText);
        assertThat(Arrays.stream(lines).noneMatch(line -> line.endsWith("-"))).isTrue();
    }

    @Test
    void breaksAWordThatIsWiderThanTheLine() throws IOException {
        String word = "X".repeat(200);

        String text = render(writer -> writer.writeWrapped(word, PdfPageWriter.FONT_TEXT, 11f, 0f));

        assertThat(text.replaceAll("\\s", "")).isEqualTo(word);
    }

    @Test
    void startsANewPageWhenTheCurrentOneIsFull() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PdfPageWriter writer = new PdfPageWriter(document);
            for (int i = 0; i < 120; i++) {
                writer.writeLine("Zeile " + i, PdfPageWriter.FONT_TEXT, 11f);
            }
            writer.close();

            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void writesCheckboxItemsWithWrappedText() throws IOException {
        String text = render(writer -> writer.writeCheckboxItem("Ölwechsel", PdfPageWriter.FONT_HEADING, 11f));

        assertThat(text).contains("Ölwechsel");
    }

    @Test
    void ignoresNullText() throws IOException {
        String text = render(writer -> {
            writer.writeLine(null, PdfPageWriter.FONT_TEXT, 11f);
            writer.writeWrapped(null, PdfPageWriter.FONT_TEXT, 11f, 0f);
        });

        assertThat(text.strip()).isEmpty();
    }

    private interface Writing {
        void write(PdfPageWriter writer) throws IOException;
    }

    private static String render(Writing writing) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PdfPageWriter writer = new PdfPageWriter(document);
            writing.write(writer);
            writer.close();
            document.save(output);
        }
        try (PDDocument document = Loader.loadPDF(output.toByteArray())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
