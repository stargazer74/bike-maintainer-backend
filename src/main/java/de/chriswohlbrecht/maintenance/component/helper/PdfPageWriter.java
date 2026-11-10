package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes text top-down onto A4 pages and starts a new page when the current one is full. Every page gets the
 * Bike Maintainer header (logo, document type, vehicle) and a footer (creation note, "page x of y"). Shared by the
 * PDF documents (maintenance report, checklist), so they all look alike.
 *
 * <p>Text is set in Inter, embedded as subset. Characters the font lacks (e.g. emoji) are replaced by {@code ?}
 * instead of failing the whole document.
 */
final class PdfPageWriter {

    /** Accent colour of the app ({@code --bm-orange}, #f97316) as RGB fractions. */
    static final float[] ORANGE = {249 / 255f, 115 / 255f, 22 / 255f};
    private static final float[] TEXT_COLOR = {17 / 255f, 24 / 255f, 39 / 255f};
    private static final float[] MUTED_COLOR = {75 / 255f, 85 / 255f, 99 / 255f};
    private static final float[] RULE_COLOR = {209 / 255f, 213 / 255f, 219 / 255f};

    private static final float MARGIN = 50f;
    private static final float LEADING = 16f;
    private static final float CHECKBOX_SIZE = 9f;
    private static final float CHECKBOX_INDENT = 18f;
    private static final float FILL_IN_LINE_HEIGHT = LEADING * 1.5f;
    /** A table row is high enough to write a measured value into it by hand. */
    private static final float TABLE_ROW_HEIGHT = 22f;
    private static final float CELL_PADDING = 4f;
    private static final float[] HEADER_FILL = {243 / 255f, 244 / 255f, 246 / 255f};
    private static final String REPLACEMENT = "?";
    private static final String ELLIPSIS = "\u2026";
    /**
     * Soft hyphen, zero-width space/joiners, bidi marks and controls, word joiner, variation selectors, BOM: not
     * meant to be seen.
     */
    private static final String INVISIBLE =
            "[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFE0E\\uFE0F\\uFEFF]";

    /** Header: logo and texts between the top edge of the page and the orange rule. */
    private static final float HEADER_TOP = 36f;
    private static final float LOGO_SIZE = 22f;
    private static final float HEADER_RULE = HEADER_TOP + LOGO_SIZE + 10f;
    /** First baseline of the content, below the header rule. */
    private static final float CONTENT_TOP = HEADER_RULE + 26f;
    /** Footer: rule and one line of small text at the bottom edge. */
    private static final float FOOTER_RULE = 50f;
    private static final float FOOTER_BASELINE = 36f;
    private static final float CONTENT_BOTTOM = FOOTER_RULE + 6f;
    private static final float FOOTER_FONT_SIZE = 8.5f;

    /** Font files, read once; every document embeds its own subset. */
    private static final Map<PdfFont, byte[]> FONT_FILES = new ConcurrentHashMap<>();

    private final PDDocument document;
    private final PdfFrame frame;
    private final Map<PdfFont, PDFont> fonts = new EnumMap<>(PdfFont.class);
    private PDPageContentStream contentStream;
    private float yPosition;
    private float pageWidth;
    private float pageHeight;

    PdfPageWriter(PDDocument document, PdfFrame frame) throws IOException {
        this.document = document;
        this.frame = frame;
        for (PdfFont font : PdfFont.values()) {
            fonts.put(font, PDType0Font.load(document, new ByteArrayInputStream(fontFile(font)), true));
        }
        startNewPage();
    }

    /** The embedded font for a style, e.g. to measure text. */
    PDFont font(PdfFont style) {
        return fonts.get(style);
    }

    /** One line without wrapping – only for short texts like labels; longer text belongs in {@link #writeWrapped}. */
    void writeLine(String text, PdfFont style, float fontSize) throws IOException {
        PDFont font = font(style);
        ensureSpace(LEADING);
        showText(sanitize(text, font), font, fontSize, MARGIN);
        yPosition -= LEADING;
    }

    /** Text wrapped at word boundaries (line breaks in the text are kept), starting {@code indent} from the margin. */
    void writeWrapped(String text, PdfFont style, float fontSize, float indent) throws IOException {
        if (text == null) {
            return;
        }
        PDFont font = font(style);
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
    void writeCheckboxItem(String text, PdfFont style, float fontSize) throws IOException {
        ensureSpace(LEADING);
        contentStream.setLineWidth(0.8f);
        contentStream.addRect(MARGIN, yPosition - 2f, CHECKBOX_SIZE, CHECKBOX_SIZE);
        contentStream.stroke();
        writeWrapped(text, style, fontSize, CHECKBOX_INDENT);
    }

    /** A horizontal line to write on by hand, labelled with {@code label} if given; takes {@link #fillInLineHeight()}. */
    void writeFillInLine(String label, PdfFont style, float fontSize) throws IOException {
        PDFont font = font(style);
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

    /**
     * One row of a table over the full content width, with a border around every cell. {@code widths} are the
     * shares of the columns and add up to 1. A header row is set on a grey background; texts that do not fit their
     * cell are shortened. Takes {@link #tableRowHeight()}.
     */
    void writeTableRow(List<PdfCell> cells, float[] widths, PdfFont style, float fontSize, boolean header)
            throws IOException {
        ensureSpace(TABLE_ROW_HEIGHT);
        drawTableRow(yPosition + LEADING - 4f, TABLE_ROW_HEIGHT, cells, widths, style, fontSize, header);
        yPosition -= TABLE_ROW_HEIGHT;
    }

    /**
     * Like {@link #writeTableRow}, but at a fixed position: {@code top} is measured from the top edge of the page.
     * Does not move the current position.
     */
    void writeTableRowAt(float top, float height, List<PdfCell> cells, float[] widths, PdfFont style, float fontSize,
                         boolean header) throws IOException {
        drawTableRow(pageHeight - top, height, cells, widths, style, fontSize, header);
    }

    private void drawTableRow(float top, float rowHeight, List<PdfCell> cells, float[] widths, PdfFont style,
                              float fontSize, boolean header) throws IOException {
        if (cells.size() != widths.length) {
            throw new IllegalArgumentException("Expected " + widths.length + " cells, got " + cells.size());
        }
        PDFont font = font(style);
        float bottom = top - rowHeight;
        float contentWidth = pageWidth - 2 * MARGIN;
        float x = MARGIN;
        for (int index = 0; index < cells.size(); index++) {
            float cellWidth = contentWidth * widths[index];
            if (header) {
                setColor(HEADER_FILL);
                contentStream.addRect(x, bottom, cellWidth, rowHeight);
                contentStream.fill();
                setColor(TEXT_COLOR);
            }
            contentStream.setStrokingColor(RULE_COLOR[0], RULE_COLOR[1], RULE_COLOR[2]);
            contentStream.setLineWidth(0.5f);
            contentStream.addRect(x, bottom, cellWidth, rowHeight);
            contentStream.stroke();
            contentStream.setStrokingColor(0f, 0f, 0f);
            PdfCell cell = cells.get(index);
            float baseline = bottom + (rowHeight - fontSize * 0.7f) / 2;
            if (cell.box()) {
                contentStream.setLineWidth(0.8f);
                float boxX = x + (cellWidth - CHECKBOX_SIZE) / 2;
                float boxY = bottom + (rowHeight - CHECKBOX_SIZE) / 2;
                contentStream.addRect(boxX, boxY, CHECKBOX_SIZE, CHECKBOX_SIZE);
                contentStream.stroke();
            } else if (cell.text() != null && !cell.text().isEmpty()) {
                String text = fit(sanitize(cell.text(), font), font, fontSize, cellWidth - 2 * CELL_PADDING);
                showText(text, font, fontSize, x + CELL_PADDING, baseline);
            }
            x += cellWidth;
        }
    }

    /** Starts a new page; the current position is the top of its content. */
    void newPage() throws IOException {
        startNewPage();
    }

    /** Moves the current position to the baseline {@code top} points below the top edge of the page. */
    void moveTo(float top) {
        yPosition = pageHeight - top;
    }

    /**
     * Text on one line at a fixed position ({@code baseline} measured from the top edge of the page), shortened to
     * {@code maxWidth}.
     */
    void writeTextAt(String text, PdfFont style, float fontSize, float x, float baseline, float maxWidth,
                     boolean muted) throws IOException {
        PDFont font = font(style);
        setColor(muted ? MUTED_COLOR : TEXT_COLOR);
        showText(fit(sanitize(text, font), font, fontSize, maxWidth), font, fontSize, x, pageHeight - baseline);
        setColor(TEXT_COLOR);
    }

    /** A filled black rectangle, {@code top} measured from the top edge of the page. */
    void fillRect(float left, float top, float width, float height) throws IOException {
        contentStream.setNonStrokingColor(0f, 0f, 0f);
        contentStream.addRect(left, pageHeight - top - height, width, height);
        contentStream.fill();
        setColor(TEXT_COLOR);
    }

    /** The outline of a rectangle in dark grey, {@code top} measured from the top edge of the page. */
    void strokeRect(float left, float top, float width, float height, float lineWidth) throws IOException {
        contentStream.setStrokingColor(MUTED_COLOR[0], MUTED_COLOR[1], MUTED_COLOR[2]);
        contentStream.setLineWidth(lineWidth);
        contentStream.addRect(left, pageHeight - top - height, width, height);
        contentStream.stroke();
        contentStream.setStrokingColor(0f, 0f, 0f);
    }

    /** Half an empty line. */
    void newLine() {
        yPosition -= LEADING / 2;
    }

    /** Text that would reach into the footer starts on a new page. */
    void ensureSpace(float height) throws IOException {
        if (yPosition - height < CONTENT_BOTTOM) {
            startNewPage();
        }
    }

    /** Finishes the last page and adds the footers, now that the number of pages is known. */
    void close() throws IOException {
        contentStream.close();
        int total = document.getNumberOfPages();
        for (int index = 0; index < total; index++) {
            PDPage page = document.getPage(index);
            try (PDPageContentStream footer = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                writeFooter(footer, index + 1, total);
            }
        }
    }

    /** Vertical space one {@link #writeFillInLine} takes, to keep a block of them together. */
    static float fillInLineHeight() {
        return FILL_IN_LINE_HEIGHT;
    }

    /** Vertical space one {@link #writeTableRow} takes, to keep a table together. */
    static float tableRowHeight() {
        return TABLE_ROW_HEIGHT;
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
        pageHeight = page.getMediaBox().getHeight();
        writeHeader();
        setColor(TEXT_COLOR);
        yPosition = pageHeight - CONTENT_TOP;
    }

    /** Logo and brand on the left, document type and subject right-aligned, orange rule below. */
    private void writeHeader() throws IOException {
        float logoBottom = pageHeight - HEADER_TOP - LOGO_SIZE;
        PdfLogo.draw(contentStream, MARGIN, logoBottom, LOGO_SIZE);

        PDFont brandFont = font(PdfFont.HEADING);
        setColor(TEXT_COLOR);
        showText("Bike Maintainer", brandFont, 12f, MARGIN + LOGO_SIZE + 8f, logoBottom + 7f);

        float right = pageWidth - MARGIN;
        float maxWidth = (pageWidth - 2 * MARGIN) / 2;
        String type = fit(sanitize(frame.documentType(), brandFont), brandFont, 11f, maxWidth);
        showText(type, brandFont, 11f, right - width(type, brandFont, 11f), logoBottom + 12f);
        PDFont textFont = font(PdfFont.TEXT);
        String subject = fit(sanitize(frame.subject(), textFont), textFont, 9f, maxWidth);
        setColor(MUTED_COLOR);
        showText(subject, textFont, 9f, right - width(subject, textFont, 9f), logoBottom);

        contentStream.setStrokingColor(ORANGE[0], ORANGE[1], ORANGE[2]);
        contentStream.setLineWidth(1.5f);
        contentStream.moveTo(MARGIN, pageHeight - HEADER_RULE);
        contentStream.lineTo(right, pageHeight - HEADER_RULE);
        contentStream.stroke();
        contentStream.setStrokingColor(0f, 0f, 0f);
    }

    private void writeFooter(PDPageContentStream footer, int page, int total) throws IOException {
        float right = pageWidth - MARGIN;
        footer.setStrokingColor(RULE_COLOR[0], RULE_COLOR[1], RULE_COLOR[2]);
        footer.setLineWidth(0.5f);
        footer.moveTo(MARGIN, FOOTER_RULE);
        footer.lineTo(right, FOOTER_RULE);
        footer.stroke();

        PDFont font = font(PdfFont.TEXT);
        footer.setNonStrokingColor(MUTED_COLOR[0], MUTED_COLOR[1], MUTED_COLOR[2]);
        float contentWidth = right - MARGIN;
        String label = fit(sanitize(frame.pageLabel().of(page, total), font), font, FOOTER_FONT_SIZE, contentWidth);
        float labelWidth = width(label, font, FOOTER_FONT_SIZE);
        // The note gets what the page label leaves; without room it is left out rather than overlapping.
        float noteWidth = contentWidth - labelWidth - 12f;
        String note = sanitize(frame.footerText(), font);
        if (noteWidth > width(ELLIPSIS, font, FOOTER_FONT_SIZE)) {
            showText(footer, fit(note, font, FOOTER_FONT_SIZE, noteWidth), font, FOOTER_FONT_SIZE, MARGIN,
                    FOOTER_BASELINE);
        }
        showText(footer, label, font, FOOTER_FONT_SIZE, right - labelWidth, FOOTER_BASELINE);
    }

    private void setColor(float[] rgb) throws IOException {
        contentStream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private void showText(String text, PDFont font, float fontSize, float x) throws IOException {
        showText(text, font, fontSize, x, yPosition);
    }

    private void showText(String text, PDFont font, float fontSize, float x, float y) throws IOException {
        showText(contentStream, text, font, fontSize, x, y);
    }

    private static void showText(PDPageContentStream stream, String text, PDFont font, float fontSize, float x,
                                 float y) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    /** The text, shortened with an ellipsis if it is wider than {@code maxWidth}. */
    private static String fit(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (width(text, font, fontSize) <= maxWidth) {
            return text;
        }
        // Shortened by whole code points, so a character outside the basic plane is never cut in half.
        int end = text.length();
        while (end > 0 && width(text.substring(0, end).stripTrailing() + ELLIPSIS, font, fontSize) > maxWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

    private static byte[] fontFile(PdfFont font) {
        return FONT_FILES.computeIfAbsent(font, key -> {
            try (InputStream input = PdfPageWriter.class.getClassLoader().getResourceAsStream(key.resource())) {
                if (input == null) {
                    throw new IllegalStateException("Font " + key.resource() + " is missing on the classpath");
                }
                return input.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read font " + key.resource(), e);
            }
        });
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
                // Whole code points only, see fit(); at least one per line.
                int minCut = rest.offsetByCodePoints(0, 1);
                int cut = rest.offsetByCodePoints(rest.length(), -1);
                while (cut > minCut && width(rest.substring(0, cut), font, fontSize) > maxWidth) {
                    cut = rest.offsetByCodePoints(cut, -1);
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
     * Control characters and line/paragraph separators become spaces, invisible formatting characters are dropped, characters the font has no
     * glyph for become {@code ?}.
     */
    static String sanitize(String text, PDFont font) {
        if (text == null) {
            return "";
        }
        // Composed form first, so that e.g. "u" + combining diaeresis (macOS) becomes "ü" instead of "u?".
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll(INVISIBLE, "");
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR) {
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
