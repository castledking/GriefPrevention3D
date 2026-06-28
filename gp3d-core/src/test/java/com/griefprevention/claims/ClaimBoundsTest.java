package com.griefprevention.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClaimBoundsTest {

    @Test
    void normalizesRectangleCorners() {
        ClaimBounds bounds = ClaimBounds.rectangle(5, 10, 4, -1, 6, 2);

        assertEquals(-1, bounds.minX());
        assertEquals(6, bounds.minY());
        assertEquals(2, bounds.minZ());
        assertEquals(5, bounds.maxX());
        assertEquals(10, bounds.maxY());
        assertEquals(4, bounds.maxZ());
        assertEquals(7, bounds.xLength());
        assertEquals(5, bounds.yHeight());
        assertEquals(3, bounds.zLength());
        assertEquals(21, bounds.area());
    }

    @Test
    void checksRectangleContainmentWithOptionalHeight() {
        ClaimBounds bounds = ClaimBounds.rectangle(0, 5, 0, 10, 9, 10);

        assertTrue(bounds.contains(5, 5, 5, false));
        assertTrue(bounds.contains(5, 9, 5, false));
        assertFalse(bounds.contains(5, 4, 5, false));
        assertTrue(bounds.contains(5, 4, 5, true));
        assertFalse(bounds.contains(11, 6, 5, true));
    }

    @Test
    void checksShapedColumnContainment() {
        ClaimBounds bounds = ClaimBounds.shaped(lShape(), 0, 255);

        assertTrue(bounds.containsColumn(0, 0));
        assertTrue(bounds.containsColumn(3, 0));
        assertTrue(bounds.containsColumn(0, 3));
        assertFalse(bounds.containsColumn(3, 3));
    }

    @Test
    void usesExactShapeForIgnoredHeightOverlap() {
        ClaimBounds shaped = ClaimBounds.shaped(lShape(), 0, 255);
        ClaimBounds missingCorner = ClaimBounds.rectangle(2, 0, 2, 3, 0, 3);
        ClaimBounds bottomStrip = ClaimBounds.rectangle(2, 0, 0, 3, 0, 0);

        assertTrue(shaped.intersects(missingCorner, true));
        assertFalse(shaped.overlaps(missingCorner, true));
        assertTrue(shaped.overlaps(bottomStrip, true));
    }

    @Test
    void supportsHeightSensitiveOverlap() {
        ClaimBounds lower = ClaimBounds.rectangle(0, 0, 0, 10, 5, 10);
        ClaimBounds upper = ClaimBounds.rectangle(0, 6, 0, 10, 8, 10);

        assertFalse(lower.overlaps(upper, false));
        assertTrue(lower.overlaps(upper, true));
    }

    private static OrthogonalPolygon lShape() {
        return OrthogonalPolygon.fromClosedPath(
            Arrays.asList(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 1),
                new OrthogonalPoint2i(1, 1),
                new OrthogonalPoint2i(1, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
            )
        );
    }
}
