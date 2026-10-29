package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes text top-down onto A4 pages and starts a new page when the current one is full. Shared by the PDF
 * documents (maintenance report, checklist).
 *
 * <p>The standard fonts only know WinAnsi characters; anything else (emoji, arrows, most non-Latin scripts) is
 * replaced by {@code ?} instead of failing the whole document.
 */
final class PdfPageWriter {

    static final PDFont FONT_TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDFont FONT_HEADING = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDFont FONT_TEXT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final float MARGIN = 50f;
    private static final float LEADING = 16f;
    private static final float CHECKBOX_SIZE = 9f;
    private static final float CHECKBOX_INDENT = 18f;
    private static final float FILL_IN_LINE_HEIGHT = LEADING * 1.5f;
    private static final String REPLACEMENT = "?";
    /** Soft hyphen, zero-width space/joiners, word joiner, variation selectors, BOM: not meant to be seen. */
    private static final String INVISIBLE = "[\\u00AD\\u200B-\\u200D\\u2060\\uFE0E\\uFE0F\\uFEFF]";

    private final PDDocument document;
    private PDPageContentStream contentStream;
    private float yPosition;
    private float pageWidth;

    PdfPageWriter(PDDocument document) throws IOException {
        this.document = document;
        startNewPage();
    }

    /** One line without wrapping – only for short texts like labels; longer text belongs in {@link #writeWrapped}. */
    void writeLine(String text, PDFont font, float fontSize) throws IOException {
        ensureSpace(LEADING);
        showText(sanitize(text, font), font, fontSize, MARGIN);
        yPosition -= LEADING;
    }

    /** Text wrapped at word boundaries (line breaks in the text are kept), starting {@code indent} from the margin. */
    void writeWrapped(String text, PDFont font, float fontSize, float indent) throws IOException {
        if (text == null) {
            return;
        }
        float maxWidth = pageWidth - 2 * MARGIN - indent;
        for (String paragraph : text.split("\\R", -1)) {
            for (String line : wrap(sanitize(paragraph, font), font, fontSize, maxWidth)) {
                ensureSpace(LEADING);
                showText(line, font, fontSize, MARGIN + indent);
                yPosition -= LEADING;
            }
        }
    }

    /** An empty box to tick by hand, followed by the wrapped text. */
    void writeCheckboxItem(String text, PDFont font, float fontSize) throws IOException {
        ensureSpace(LEADING);
        contentStream.setLineWidth(0.8f);
        contentStream.addRect(MARGIN, yPosition - 2f, CHECKBOX_SIZE, CHECKBOX_SIZE);
        contentStream.stroke();
        writeWrapped(text, font, fontSize, CHECKBOX_INDENT);
    }

    /** A horizontal line to write on by hand, labelled with {@code label} if given; takes {@link #fillInLineHeight()}. */
    void writeFillInLine(String label, PDFont font, float fontSize) throws IOException {
        ensureSpace(FILL_IN_LINE_HEIGHT);
        yPosition -= LEADING / 2;
        float lineStart = MARGIN;
        if (label != null && !label.isBlank()) {
            String sanitized = sanitize(label, font);
            showText(sanitized, font, fontSize, MARGIN);
            lineStart = MARGIN + font.getStringWidth(sanitized) / 1000 * fontSize + 6f;
        }
        contentStream.setLineWidth(0.5f);
        contentStream.moveTo(lineStart, yPosition - 2f);
        contentStream.lineTo(pageWidth - MARGIN, yPosition - 2f);
        contentStream.stroke();
        yPosition -= LEADING;
    }

    /** Half an empty line. */
    void newLine() {
        yPosition -= LEADING / 2;
    }

    /** Text that is still shown after the margin is reached starts on a new page. */
    void ensureSpace(float height) throws IOException {
        if (yPosition - height < MARGIN) {
            startNewPage();
        }
    }

    void close() throws IOException {
        contentStream.close();
    }

    /** Vertical space one {@link #writeFillInLine} takes, to keep a block of them together. */
    static float fillInLineHeight() {
        return FILL_IN_LINE_HEIGHT;
    }

    /** Indent of the text of a checkbox item, for lines that belong to it. */
    static float checkboxIndent() {
        return CHECKBOX_INDENT;
    }

    private void startNewPage() throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        pageWidth = page.getMediaBox().getWidth();
        yPosition = page.getMediaBox().getHeight() - MARGIN;
    }

    private void showText(String text, PDFont font, float fontSize, float x) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, yPosition);
        contentStream.showText(text);
        contentStream.endText();
    }

    private static List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, font, fontSize) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // A single word wider than the line is split at the last character that still fits.
            String rest = word;
            while (width(rest, font, fontSize) > maxWidth) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(rest.substring(0, cut), font, fontSize) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static float width(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }

    /**
     * Control characters become spaces, invisible formatting characters are dropped, characters the font cannot
     * encode become {@code ?}.
     */
    static String sanitize(String text, PDFont font) {
        if (text == null) {
            return "";
        }
        // Composed form first, so that e.g. "u" + combining diaeresis (macOS) becomes "ü" instead of "u?".
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll(INVISIBLE, "");
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                result.append(' ');
                return;
            }
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                result.append(character);
            } catch (IOException | IllegalArgumentException e) {
                result.append(REPLACEMENT);
            }
        });
        return result.toString();
    }
}
