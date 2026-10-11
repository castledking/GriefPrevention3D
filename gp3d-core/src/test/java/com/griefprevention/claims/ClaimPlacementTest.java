package com.griefprevention.claims;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimPlacementTest
{
    private static final String WORLD = "world";
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final int FLOOR = 40;
    private static final int TOP = 319;

    /** A 2D claim over x/z 0..99, from y 40 up. */
    private final ClaimSnapshot parent = new ClaimSnapshot(1L, WORLD, OWNER, null,
            ClaimBounds.rectangle(0, FLOOR, 0, 99, TOP, 99), false, false);

    @Test
    void aSubdivisionMustStayInItsParentsColumns()
    {
        assertTrue(ClaimPlacement.fitsInParent(this.parent, ClaimBounds.rectangle(0, FLOOR, 0, 99, TOP, 99), false));
        assertTrue(ClaimPlacement.fitsInParent(this.parent, ClaimBounds.rectangle(10, FLOOR, 10, 20, TOP, 20), false));
        assertFalse(ClaimPlacement.fitsInParent(this.parent, ClaimBounds.rectangle(90, FLOOR, 90, 100, TOP, 95), false));
    }

    @Test
    void aThreeDimensionalSubdivisionStaysAboveItsParentsFloor()
    {
        assertTrue(ClaimPlacement.fitsInParent(this.parent, ClaimBounds.rectangle(10, 60, 10, 20, 70, 20), true));
        assertFalse(ClaimPlacement.fitsInParent(this.parent, ClaimBounds.rectangle(10, 30, 10, 20, 70, 20), true));
    }

    @Test
    void twoDimensionalSiblingsConflictInAnySharedColumn()
    {
        ClaimSnapshot existing = subdivision(2L, ClaimBounds.rectangle(10, FLOOR, 10, 20, TOP, 20), false);
        ClaimSnapshot touching = subdivision(null, ClaimBounds.rectangle(20, FLOOR, 20, 30, TOP, 30), false);
        ClaimSnapshot apart = subdivision(null, ClaimBounds.rectangle(21, FLOOR, 21, 30, TOP, 30), false);

        assertEquals(existing, ClaimPlacement.overlappingSibling(touching, Collections.singletonList(existing)));
        assertNull(ClaimPlacement.overlappingSibling(apart, Collections.singletonList(existing)));
    }

    @Test
    void threeDimensionalSubdivisionsMayStackButNotMeetATwoDimensionalOne()
    {
        ClaimSnapshot ground = subdivision(2L, ClaimBounds.rectangle(10, 60, 10, 20, 70, 20), true);
        ClaimSnapshot above = subdivision(null, ClaimBounds.rectangle(10, 71, 10, 20, 80, 20), true);
        ClaimSnapshot meeting = subdivision(null, ClaimBounds.rectangle(10, 70, 10, 20, 80, 20), true);
        ClaimSnapshot column = subdivision(null, ClaimBounds.rectangle(15, FLOOR, 15, 25, TOP, 25), false);

        assertNull(ClaimPlacement.overlappingSibling(above, Collections.singletonList(ground)));
        assertEquals(ground, ClaimPlacement.overlappingSibling(meeting, Collections.singletonList(ground)));
        assertEquals(ground, ClaimPlacement.overlappingSibling(column, Collections.singletonList(ground)));
    }

    @Test
    void aSubdivisionIsNotItsOwnSibling()
    {
        ClaimSnapshot existing = subdivision(2L, ClaimBounds.rectangle(10, FLOOR, 10, 20, TOP, 20), false);
        ClaimSnapshot resized = subdivision(2L, ClaimBounds.rectangle(10, FLOOR, 10, 30, TOP, 30), false);

        assertNull(ClaimPlacement.overlappingSibling(resized, Collections.singletonList(existing)));
    }

    @Test
    void aClaimCannotShrinkPastOneOfItsSubdivisions()
    {
        ClaimSnapshot inside = subdivision(2L, ClaimBounds.rectangle(10, FLOOR, 10, 20, TOP, 20), false);
        ClaimSnapshot nearEdge = subdivision(3L, ClaimBounds.rectangle(80, FLOOR, 80, 95, TOP, 95), false);
        ClaimSnapshot shrunk = new ClaimSnapshot(1L, WORLD, OWNER, null,
                ClaimBounds.rectangle(0, FLOOR, 0, 90, TOP, 90), false, false);

        assertEquals(nearEdge, ClaimPlacement.subdivisionLeftOutside(shrunk, Arrays.asList(inside, nearEdge)));
        assertNull(ClaimPlacement.subdivisionLeftOutside(this.parent, Arrays.asList(inside, nearEdge)));
    }

    @Test
    void aShapedParentsNotchIsOutsideIt()
    {
        // An L: the square x/z 0..9 plus the arm x 0..19, z 0..4. The notch x 10..19, z 5..9 is not claimed.
        OrthogonalPolygon outline = OrthogonalPolygon.fromClosedPath(Arrays.asList(
                new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(19, 0), new OrthogonalPoint2i(19, 4),
                new OrthogonalPoint2i(9, 4), new OrthogonalPoint2i(9, 9), new OrthogonalPoint2i(0, 9),
                new OrthogonalPoint2i(0, 0)));
        ClaimBounds shaped = ClaimBounds.shaped(outline, FLOOR, TOP);

        assertTrue(ClaimPlacement.containsColumns(shaped, ClaimBounds.rectangle(12, FLOOR, 1, 18, TOP, 3)));
        assertTrue(ClaimPlacement.containsColumns(shaped, ClaimBounds.rectangle(1, FLOOR, 1, 8, TOP, 8)));
        assertFalse(ClaimPlacement.containsColumns(shaped, ClaimBounds.rectangle(12, FLOOR, 6, 18, TOP, 8)));
        assertFalse(ClaimPlacement.containsColumns(shaped, ClaimBounds.rectangle(5, FLOOR, 2, 15, TOP, 7)));
    }

    private static ClaimSnapshot subdivision(Long id, ClaimBounds bounds, boolean threeDimensional)
    {
        return new ClaimSnapshot(id, WORLD, null, 1L, bounds, threeDimensional, true);
    }
}
