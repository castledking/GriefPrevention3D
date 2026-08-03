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
    void usesExactShapeForHeightSensitiveOverlapWithThreeDimensionalBounds() {
        ClaimBounds shaped = ClaimBounds.shaped(uShape(), 0, 255);
        // 3D claim sitting entirely inside the shaped claim's rectangular bounding box but
        // only over the cavity (the U's enclosed notch), which the shaped claim does not cover.
        ClaimBounds cavity3d = ClaimBounds.rectangle(2, 20, 2, 3, 30, 4);
        // 3D claim whose X/Z columns do overlap the actual shaped area.
        ClaimBounds covering3d = ClaimBounds.rectangle(4, 20, 1, 5, 30, 4);
        // 3D claim whose columns only overlap the cavity, but is vertically separated.
        ClaimBounds farBelow3d = ClaimBounds.rectangle(2, -50, 2, 3, -40, 4);

        assertTrue(shaped.intersects(cavity3d, false));
        assertFalse(shaped.overlaps(cavity3d, false));
        assertTrue(shaped.overlaps(covering3d, false));
        // Y separation must still be respected when a shaped claim is involved.
        assertFalse(shaped.overlaps(farBelow3d, false));
    }

    @Test
    void cavityColumnsAreNotPartOfShapedClaim() {
        ClaimBounds shaped = ClaimBounds.shaped(uShape(), 0, 255);

        assertTrue(shaped.containsColumn(0, 0));
        assertTrue(shaped.containsColumn(4, 2));
        assertTrue(shaped.containsColumn(1, 4));
        assertFalse(shaped.containsColumn(2, 2));
        assertFalse(shaped.containsColumn(3, 4));
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

    /**
     * A 6x6 square (x=0..5, z=0..5) with a cavity carved into its interior: columns
     * x=1..4, z=1..5 are removed, leaving a U-shaped remainder open at the top (z=5).
     * The cavity columns sit inside the rectangular bounding box but are not covered by
     * the polygon, mirroring the shaped-circle-with-cavity scenario.
     */
    private static OrthogonalPolygon uShape() {
        return OrthogonalPolygon.fromClosedPath(
            Arrays.asList(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(5, 5),
                new OrthogonalPoint2i(4, 5),
                new OrthogonalPoint2i(4, 1),
                new OrthogonalPoint2i(1, 1),
                new OrthogonalPoint2i(1, 5),
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(0, 0)
            )
        );
    }

    @Test
    void supportsHeightSensitiveOverlap() {
        ClaimBounds lower = ClaimBounds.rectangle(0, 0, 0, 10, 5, 10);
        ClaimBounds upper = ClaimBounds.rectangle(0, 6, 0, 10, 8, 10);

        assertFalse(lower.overlaps(upper, false));
        assertTrue(lower.overlaps(upper, true));
    }
}
