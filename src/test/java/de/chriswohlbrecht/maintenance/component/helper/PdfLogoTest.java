package de.chriswohlbrecht.maintenance.component.helper;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * The SVG path interpreter behind the logo. Origin (0, 0) and scale 1, so PDF coordinates equal SVG coordinates with
 * the y-axis flipped.
 */
class PdfLogoTest {

    private final PDPageContentStream stream = mock(PDPageContentStream.class);
    private final PdfLogo.PathDrawer drawer = new PdfLogo.PathDrawer(stream, 0f, 0f, 1f);

    @Test
    void absoluteAndRelativeLinesAndClose() throws IOException {
        drawer.draw("M10-20 30-40h5v-5l1 1H50V-60z m1 1 2 2Z");

        InOrder order = inOrder(stream);
        order.verify(stream).moveTo(10f, 20f);
        // Further pairs after M are absolute lines.
        order.verify(stream).lineTo(30f, 40f);
        order.verify(stream).lineTo(35f, 40f);
        order.verify(stream).lineTo(35f, 45f);
        order.verify(stream).lineTo(36f, 44f);
        order.verify(stream).lineTo(50f, 44f);
        order.verify(stream).lineTo(50f, 60f);
        order.verify(stream).closePath();
        // After z the current point is the start of the subpath again; pairs after m are relative lines.
        order.verify(stream).moveTo(11f, 19f);
        order.verify(stream).lineTo(13f, 17f);
        order.verify(stream).closePath();
    }

    @Test
    void smoothCubicReflectsThePreviousControlPoint() throws IOException {
        drawer.draw("M0 0C0-10 10-10 10 0S20 10 20 0");

        InOrder order = inOrder(stream);
        order.verify(stream).curveTo(0f, 10f, 10f, 10f, 10f, 0f);
        order.verify(stream).curveTo(10f, -10f, 20f, -10f, 20f, 0f);
    }

    @Test
    void smoothCubicWithoutPreviousCurveStartsAtTheCurrentPoint() throws IOException {
        drawer.draw("M0 0s10 10 10 0");

        inOrder(stream).verify(stream).curveTo(0f, 0f, 10f, -10f, 10f, 0f);
    }

    @Test
    void quadraticCurvesBecomeCubicOnesAndSmoothOnesReflect() throws IOException {
        drawer.draw("M0 0Q30 0 30 30T30 90");

        InOrder order = inOrder(stream);
        order.verify(stream).curveTo(20f, 0f, 30f, -10f, 30f, -30f);
        // Reflected control point (30, 60): 2/3 of the way from (30, 30) and from (30, 90).
        order.verify(stream).curveTo(30f, -50f, 30f, -70f, 30f, -90f);
    }

    @Test
    void smoothQuadraticWithoutPreviousCurveUsesTheCurrentPoint() throws IOException {
        drawer.draw("M0 0t30 0");

        inOrder(stream).verify(stream).curveTo(0f, 0f, 10f, 0f, 30f, 0f);
    }
}
