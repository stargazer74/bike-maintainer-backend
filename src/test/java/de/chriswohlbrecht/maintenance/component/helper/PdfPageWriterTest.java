package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPageWriterTest {

    private static final PdfFrame FRAME = new PdfFrame("Wartungshistorie", "Die Rote",
            "Erstellt mit Bike Maintainer am 30.10.2026", (page, total) -> "Seite " + page + " von " + total);

    @Test
    void keepsCharactersOfTheEmbeddedFontAndReplacesOnlyMissingOnes() throws IOException {
        String text = render(writer -> writer.writeLine("Kette 🔧 → prüfen 12 € ½ “gut”", PdfFont.TEXT, 11f));

        assertThat(text).contains("Kette ? → prüfen 12 € ½ “gut”");
    }

    @Test
    void showsDecomposedUmlautsAndDropsInvisibleCharacters() throws IOException {
        String text = render(writer -> writer.writeLine(
                "Müller Brems­beläge Ket​te ❤️", PdfFont.TEXT, 11f));

        assertThat(text).contains("Müller Bremsbeläge Kette").doesNotContain("??");
    }

    @Test
    void embedsInterAsSubset() throws IOException {
        byte[] pdf = renderPdf(writer -> writer.writeLine("Ölwechsel", PdfFont.TEXT, 11f));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<String> fontNames = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                for (org.apache.pdfbox.cos.COSName name : page.getResources().getFontNames()) {
                    PDFont font = page.getResources().getFont(name);
                    assertThat(font).isInstanceOf(PDType0Font.class);
                    assertThat(font.isEmbedded()).isTrue();
                    fontNames.add(font.getName());
                }
            }
            // Subset fonts carry a six-letter tag, e.g. "ABCDEF+Inter-Regular".
            assertThat(fontNames).anyMatch(name -> name.matches("[A-Z]{6}\\+Inter-Regular"));
        }
        assertThat(pdf.length).isLessThan(60_000);
    }

    @Test
    void wrapsLongTextAtWordBoundaries() throws IOException {
        String longText = "Bremsbeläge vorne und hinten prüfen, Bremsflüssigkeit wechseln, Bremsscheiben auf "
                + "Riefen und Mindeststärke kontrollieren, Bremssättel reinigen und Kolben gangbar machen.";

        String text = render(writer -> writer.writeWrapped(longText, PdfFont.TEXT, 11f, 0f));

        List<String> lines = bodyLines(text);
        assertThat(lines.size()).isGreaterThan(1);
        assertThat(String.join(" ", lines)).isEqualTo(longText);
        assertThat(lines.stream().noneMatch(line -> line.endsWith("-"))).isTrue();
    }

    @Test
    void breaksAWordThatIsWiderThanTheLine() throws IOException {
        String word = "X".repeat(200);

        String text = render(writer -> writer.writeWrapped(word, PdfFont.TEXT, 11f, 0f));

        assertThat(String.join("", bodyLines(text))).isEqualTo(word);
    }

    @Test
    void startsANewPageWhenTheCurrentOneIsFull() throws IOException {
        byte[] pdf = renderPdf(writer -> {
            for (int i = 0; i < 120; i++) {
                writer.writeLine("Zeile " + i, PdfFont.TEXT, 11f);
            }
        });

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void everyPageHasTheHeaderAndAFooterWithPageNumberOfTotal() throws IOException {
        byte[] pdf = renderPdf(writer -> {
            for (int i = 0; i < 120; i++) {
                writer.writeLine("Zeile " + i, PdfFont.TEXT, 11f);
            }
        });

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int total = document.getNumberOfPages();
            for (int page = 1; page <= total; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                assertThat(text)
                        .contains("Bike Maintainer")
                        .contains("Wartungshistorie")
                        .contains("Die Rote")
                        .contains("Erstellt mit Bike Maintainer am 30.10.2026")
                        .contains("Seite " + page + " von " + total);
            }
        }
    }

    @Test
    void bodyTextStaysBetweenHeaderAndFooter() throws IOException {
        byte[] pdf = renderPdf(writer -> {
            for (int i = 0; i < 120; i++) {
                writer.writeWrapped("Zeile " + i + " " + "lang ".repeat(i % 30), PdfFont.TEXT, 11f, 0f);
            }
        });

        try (PDDocument document = Loader.loadPDF(pdf)) {
            float pageHeight = document.getPage(0).getMediaBox().getHeight();
            List<Float> bodyTops = new ArrayList<>();
            List<Float> bodyBottoms = new ArrayList<>();
            List<Float> headerBottoms = new ArrayList<>();
            List<Float> footerTops = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    // y is measured from the top of the page, at the baseline.
                    float baseline = positions.getFirst().getYDirAdj();
                    float top = baseline - positions.getFirst().getHeightDir();
                    if (text.startsWith("Zeile") || text.startsWith("lang")) {
                        bodyTops.add(top);
                        bodyBottoms.add(baseline);
                    } else if (text.contains("Wartungshistorie") || text.contains("Die Rote")
                            || text.contains("Bike Maintainer") && !text.startsWith("Erstellt")) {
                        headerBottoms.add(baseline);
                    } else if (text.startsWith("Erstellt") || text.startsWith("Seite")) {
                        footerTops.add(baseline - positions.getFirst().getHeightDir());
                    }
                }
            };
            stripper.getText(document);

            assertThat(bodyTops).isNotEmpty();
            assertThat(Math.ceil(bodyTops.stream().min(Float::compare).orElseThrow()))
                    .isGreaterThan(headerBottoms.stream().max(Float::compare).orElseThrow());
            assertThat(bodyBottoms.stream().max(Float::compare).orElseThrow())
                    .isLessThan(footerTops.stream().min(Float::compare).orElseThrow());
            assertThat(footerTops.stream().max(Float::compare).orElseThrow()).isLessThan(pageHeight);
        }
    }

    @Test
    void shortensAVehicleNameThatDoesNotFitIntoTheHeader() throws IOException {
        String longName = "Kawasaki VN800 Classic mit Seitenwagen und sehr langem Spitznamen aus der Garage";
        PdfFrame frame = new PdfFrame("Wartungshistorie", longName, "Erstellt", (page, total) -> "Seite");

        String text = render(frame, writer -> writer.writeLine("Inhalt", PdfFont.TEXT, 11f));

        assertThat(text).contains("Kawasaki VN800").contains("…").doesNotContain("aus der Garage");
    }

    @Test
    void neverSplitsCharactersOutsideTheBasicPlane() throws IOException {
        String squared = "🄰";
        PdfFrame frame = new PdfFrame("Wartungshistorie", "x".repeat(37) + squared.repeat(80), "Erstellt",
                (page, total) -> "Seite " + page + " von " + total);

        String text = render(frame, writer -> writer.writeWrapped(squared.repeat(120), PdfFont.TEXT, 11f, 0f));

        assertThat(text).contains(squared).doesNotContain("?");
    }

    @Test
    void dropsBidiControlsAndTreatsLineSeparatorsAsSpaces() throws IOException {
        String text = render(writer -> writer.writeLine(
                "A‎B‏C‮D⁦E⁩F G H I", PdfFont.TEXT, 11f));

        assertThat(text).contains("ABCDEF G H I").doesNotContain("?");
    }

    @Test
    void keepsALongPageLabelOnThePage() throws IOException {
        String label = "Seite eins von insgesamt sehr vielen Seiten dieses ausgesprochen ausführlichen Dokuments "
                .repeat(3);
        PdfFrame frame = new PdfFrame("Wartungshistorie", "Die Rote", "Erstellt mit Bike Maintainer",
                (page, total) -> label);

        byte[] pdf = renderPdf(frame, writer -> writer.writeLine("Inhalt", PdfFont.TEXT, 11f));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            float pageWidth = document.getPage(0).getMediaBox().getWidth();
            List<String> footerLines = new ArrayList<>();
            List<Float> footerStarts = new ArrayList<>();
            List<Float> footerEnds = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    // The text extraction drops characters outside the page, so check what is left of the label.
                    if (positions.getFirst().getYDirAdj() > 780f) {
                        footerLines.add(text);
                        footerStarts.add(positions.getFirst().getXDirAdj());
                        TextPosition last = positions.getLast();
                        footerEnds.add(last.getXDirAdj() + last.getWidthDirAdj());
                    }
                }
            };
            stripper.getText(document);

            assertThat(footerLines).singleElement().satisfies(line -> assertThat(line)
                    .startsWith("Seite eins von insgesamt").endsWith("\u2026"));
            assertThat(footerStarts).allMatch(x -> x >= 49f);
            assertThat(footerEnds).allMatch(x -> x <= pageWidth - 49f);
        }
    }

    @Test
    void writesCheckboxItemsWithWrappedText() throws IOException {
        String text = render(writer -> writer.writeCheckboxItem("Ölwechsel", PdfFont.HEADING, 11f));

        assertThat(text).contains("Ölwechsel");
    }

    @Test
    void ignoresNullText() throws IOException {
        String text = render(writer -> {
            writer.writeLine(null, PdfFont.TEXT, 11f);
            writer.writeWrapped(null, PdfFont.TEXT, 11f, 0f);
        });

        assertThat(bodyLines(text)).isEmpty();
    }

    private interface Writing {
        void write(PdfPageWriter writer) throws IOException;
    }

    /** Text lines without header and footer. */
    private static List<String> bodyLines(String text) {
        return Arrays.stream(text.strip().split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.contains("Bike Maintainer") && !line.contains("Wartungshistorie")
                        && !line.contains("Die Rote") && !line.startsWith("Seite "))
                .toList();
    }

    private static String render(Writing writing) throws IOException {
        return render(FRAME, writing);
    }

    private static String render(PdfFrame frame, Writing writing) throws IOException {
        try (PDDocument document = Loader.loadPDF(renderPdf(frame, writing))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static byte[] renderPdf(Writing writing) throws IOException {
        return renderPdf(FRAME, writing);
    }

    private static byte[] renderPdf(PdfFrame frame, Writing writing) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PdfPageWriter writer = new PdfPageWriter(document, frame);
            writing.write(writer);
            writer.close();
            document.save(output);
        }
        return output.toByteArray();
    }
}
