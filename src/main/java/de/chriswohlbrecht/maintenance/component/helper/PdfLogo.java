package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app logo as vector graphic: the Material Symbol "two_wheeler" (Apache License 2.0, Google) in white on an
 * orange rounded square, like in the app header.
 */
final class PdfLogo {

    /** Path of {@code two_wheeler_24px.svg} (Material Symbols Outlined), viewBox {@code 0 -960 960 960}. */
    private static final String ICON_PATH = "M160-200q-66 0-113-47T0-360q0-57 36.5-101t93.5-55l-28-24H0v-60h180"
            + "l100 60 160-60h126l-62-80H400v-80h142l84 108 134-68v120h-92l70 92q15-6 30.5-9t31.5-3q66 0 113 47t47 113"
            + "q0 66-47 113t-113 47q-66 0-113-47t-47-113q0-27 9.5-52.5T676-460l-20-24-136 204H400l-80-70q-5 63-51 "
            + "106.5T160-200Zm0-80q33 0 56.5-23.5T240-360q0-33-23.5-56.5T160-440q-33 0-56.5 23.5T80-360q0 33 23.5 "
            + "56.5T160-280Zm294-240-144 54 144-54h130-130Zm346 240q33 0 56.5-23.5T880-360q0-33-23.5-56.5T800-440"
            + "q-33 0-56.5 23.5T720-360q0 33 23.5 56.5T800-280Zm-322-80 106-160H454l-144 54 120 106h48Z";
    private static final float VIEW_BOX = 960f;
    private static final Pattern TOKEN = Pattern.compile("[MmLlHhVvCcSsQqTtZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)");

    private PdfLogo() {
    }

    /** Draws the logo with its lower left corner at ({@code x}, {@code y}). */
    static void draw(PDPageContentStream stream, float x, float y, float size) throws IOException {
        stream.saveGraphicsState();
        stream.setNonStrokingColor(PdfPageWriter.ORANGE[0], PdfPageWriter.ORANGE[1], PdfPageWriter.ORANGE[2]);
        roundedRect(stream, x, y, size, size, size * 0.25f);
        stream.fill();
        float iconSize = size * 0.7f;
        float offset = (size - iconSize) / 2;
        stream.setNonStrokingColor(1f, 1f, 1f);
        new PathDrawer(stream, x + offset, y + offset, iconSize / VIEW_BOX).draw(ICON_PATH);
        stream.fill();
        stream.restoreGraphicsState();
    }

    private static void roundedRect(PDPageContentStream stream, float x, float y, float width, float height,
                                    float radius) throws IOException {
        // Bézier approximation of a quarter circle.
        float k = radius * 0.5523f;
        stream.moveTo(x + radius, y);
        stream.lineTo(x + width - radius, y);
        stream.curveTo(x + width - radius + k, y, x + width, y + radius - k, x + width, y + radius);
        stream.lineTo(x + width, y + height - radius);
        stream.curveTo(x + width, y + height - radius + k, x + width - radius + k, y + height,
                x + width - radius, y + height);
        stream.lineTo(x + radius, y + height);
        stream.curveTo(x + radius - k, y + height, x, y + height - radius + k, x, y + height - radius);
        stream.lineTo(x, y + radius);
        stream.curveTo(x, y + radius - k, x + radius - k, y, x + radius, y);
        stream.closePath();
    }

    /**
     * Minimal SVG path interpreter for the commands Material Symbols use (no arcs). SVG coordinates grow downwards
     * and the view box spans y from -960 to 0, so y = 0 is the bottom of the icon.
     */
    private static final class PathDrawer {
        private final PDPageContentStream stream;
        private final float originX;
        private final float originY;
        private final float scale;
        private float currentX;
        private float currentY;
        private float startX;
        private float startY;
        /** Last control point, for the smooth curve commands S and T. */
        private float controlX;
        private float controlY;
        private char previous;

        private PathDrawer(PDPageContentStream stream, float originX, float originY, float scale) {
            this.stream = stream;
            this.originX = originX;
            this.originY = originY;
            this.scale = scale;
        }

        private void draw(String path) throws IOException {
            List<String> tokens = new ArrayList<>();
            Matcher matcher = TOKEN.matcher(path);
            while (matcher.find()) {
                tokens.add(matcher.group());
            }
            int index = 0;
            char command = 'M';
            while (index < tokens.size()) {
                String token = tokens.get(index);
                if (Character.isLetter(token.charAt(0))) {
                    command = token.charAt(0);
                    index++;
                    if (command == 'Z' || command == 'z') {
                        stream.closePath();
                        currentX = startX;
                        currentY = startY;
                        previous = command;
                        continue;
                    }
                }
                index = apply(command, tokens, index);
                // Further coordinate pairs after a move are lines.
                if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                }
            }
        }

        private int apply(char command, List<String> tokens, int index) throws IOException {
            boolean relative = Character.isLowerCase(command);
            float baseX = relative ? currentX : 0;
            float baseY = relative ? currentY : 0;
            int next = index;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    currentX = baseX + number(tokens, next++);
                    currentY = baseY + number(tokens, next++);
                    startX = currentX;
                    startY = currentY;
                    stream.moveTo(pdfX(currentX), pdfY(currentY));
                }
                case 'L' -> {
                    currentX = baseX + number(tokens, next++);
                    currentY = baseY + number(tokens, next++);
                    stream.lineTo(pdfX(currentX), pdfY(currentY));
                }
                case 'H' -> {
                    currentX = baseX + number(tokens, next++);
                    stream.lineTo(pdfX(currentX), pdfY(currentY));
                }
                case 'V' -> {
                    currentY = baseY + number(tokens, next++);
                    stream.lineTo(pdfX(currentX), pdfY(currentY));
                }
                case 'C' -> {
                    float x1 = baseX + number(tokens, next++);
                    float y1 = baseY + number(tokens, next++);
                    float x2 = baseX + number(tokens, next++);
                    float y2 = baseY + number(tokens, next++);
                    cubic(x1, y1, x2, y2, baseX + number(tokens, next++), baseY + number(tokens, next++));
                }
                case 'S' -> {
                    boolean smooth = "CcSs".indexOf(previous) >= 0;
                    float x1 = smooth ? 2 * currentX - controlX : currentX;
                    float y1 = smooth ? 2 * currentY - controlY : currentY;
                    float x2 = baseX + number(tokens, next++);
                    float y2 = baseY + number(tokens, next++);
                    cubic(x1, y1, x2, y2, baseX + number(tokens, next++), baseY + number(tokens, next++));
                }
                case 'Q' -> {
                    float qx = baseX + number(tokens, next++);
                    float qy = baseY + number(tokens, next++);
                    quadratic(qx, qy, baseX + number(tokens, next++), baseY + number(tokens, next++));
                }
                case 'T' -> {
                    boolean smooth = "QqTt".indexOf(previous) >= 0;
                    float qx = smooth ? 2 * currentX - controlX : currentX;
                    float qy = smooth ? 2 * currentY - controlY : currentY;
                    quadratic(qx, qy, baseX + number(tokens, next++), baseY + number(tokens, next++));
                }
                default -> throw new IllegalArgumentException("Unsupported SVG path command " + command);
            }
            previous = command;
            return next;
        }

        private void cubic(float x1, float y1, float x2, float y2, float x, float y) throws IOException {
            stream.curveTo(pdfX(x1), pdfY(y1), pdfX(x2), pdfY(y2), pdfX(x), pdfY(y));
            controlX = x2;
            controlY = y2;
            currentX = x;
            currentY = y;
        }

        /** PDF only has cubic curves; a quadratic one is the cubic with control points 2/3 of the way. */
        private void quadratic(float qx, float qy, float x, float y) throws IOException {
            float x1 = currentX + 2f / 3 * (qx - currentX);
            float y1 = currentY + 2f / 3 * (qy - currentY);
            float x2 = x + 2f / 3 * (qx - x);
            float y2 = y + 2f / 3 * (qy - y);
            stream.curveTo(pdfX(x1), pdfY(y1), pdfX(x2), pdfY(y2), pdfX(x), pdfY(y));
            controlX = qx;
            controlY = qy;
            currentX = x;
            currentY = y;
        }

        private float pdfX(float svgX) {
            return originX + svgX * scale;
        }

        private float pdfY(float svgY) {
            return originY - svgY * scale;
        }

        private static float number(List<String> tokens, int index) {
            return Float.parseFloat(tokens.get(index));
        }
    }
}
