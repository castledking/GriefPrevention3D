package me.ryanhamshire.GriefPrevention;

import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MAX_X;
import static me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.MIN_X;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;
import me.ryanhamshire.GriefPrevention.ScrollResizeGeometry.Face;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ScrollResizeGeometryTest {

    @Test
    void scrollingUpIsPositiveAndScrollingDownIsNegative() {
        assertEquals(1, ScrollResizeGeometry.scrollStep(3, 2, 3));
        assertEquals(-1, ScrollResizeGeometry.scrollStep(3, 4, 3));
    }

    @Test
    void scrollingWrapsAroundTheHotbar() {
        assertEquals(1, ScrollResizeGeometry.scrollStep(0, 8, 0));
        assertEquals(-1, ScrollResizeGeometry.scrollStep(8, 0, 8));
    }

    @Test
    void fastScrollingCountsFromTheLastSlotTheClientSent() {
        // The server reset the slot to 3, but the client already moved to 4 and scrolled on to 5.
        assertEquals(-1, ScrollResizeGeometry.scrollStep(3, 5, 4));
    }

    @Test
    void numberKeysAreNotScrolling() {
        assertEquals(0, ScrollResizeGeometry.scrollStep(3, 6, 3));
    }

    @Test
    void insideAClaimTheCameraPicksAnySide() {
        EnumSet<Face> all2D = ScrollResizeGeometry.allFaces(false);
        assertEquals(Face.EAST, ScrollResizeGeometry.pickFace(1, 0, 0.2, all2D));
        assertEquals(Face.NORTH, ScrollResizeGeometry.pickFace(0.1, 0, -1, all2D));
        assertEquals(Face.SOUTH, ScrollResizeGeometry.pickFace(0.1, 0.95, 0.2, all2D), "2D claims have no top");

        EnumSet<Face> all3D = ScrollResizeGeometry.allFaces(true);
        assertEquals(Face.UP, ScrollResizeGeometry.pickFace(0.1, 0.9, 0.1, all3D));
        assertEquals(Face.DOWN, ScrollResizeGeometry.pickFace(0.1, -0.9, 0.1, all3D));
    }

    @Test
    void aCornerSelectionOnlyReachesTheSidesAtThatCorner() {
        int[] bounds = {0, 60, 0, 10, 60, 10};
        EnumSet<Face> faces = ScrollResizeGeometry.cornerFaces(bounds, 10, 60, 0, false);

        assertEquals(EnumSet.of(Face.EAST, Face.NORTH), faces);
        // Looking west still moves the east side: the camera only picks the axis.
        assertEquals(Face.EAST, ScrollResizeGeometry.pickFace(-1, 0, 0, faces));
        assertEquals(Face.NORTH, ScrollResizeGeometry.pickFace(0, 0, 1, faces));
    }

    @Test
    void a3DCornerAddsTheTopOrBottom() {
        int[] bounds = {0, 70, 0, 5, 80, 5};
        assertEquals(EnumSet.of(Face.WEST, Face.SOUTH, Face.UP), ScrollResizeGeometry.cornerFaces(bounds, 0, 80, 5, true));
        assertEquals(EnumSet.of(Face.EAST, Face.NORTH, Face.DOWN), ScrollResizeGeometry.cornerFaces(bounds, 5, 70, 0, true));
    }

    @Test
    void aPointThatIsNotACornerLeavesEverySide() {
        int[] bounds = {0, 60, 0, 10, 60, 10};
        assertEquals(ScrollResizeGeometry.allFaces(false), ScrollResizeGeometry.cornerFaces(bounds, 4, 60, 0, false));
    }

    @Test
    void stepsMoveOneSideAndNeverCollapseTheClaim() {
        int[] bounds = {0, 60, 0, 10, 60, 10};

        assertArrayEquals(new int[] {0, 60, 0, 11, 60, 10}, ScrollResizeGeometry.step(bounds, Face.EAST, 1));
        assertArrayEquals(new int[] {-1, 60, 0, 10, 60, 10}, ScrollResizeGeometry.step(bounds, Face.WEST, 1));
        assertArrayEquals(new int[] {0, 60, 0, 10, 60, 9}, ScrollResizeGeometry.step(bounds, Face.SOUTH, -1));

        int[] thin = {0, 60, 0, 0, 60, 10};
        assertNull(ScrollResizeGeometry.step(thin, Face.EAST, -1));
    }

    @Test
    void aShapedCornerPicksTheEdgeTheCameraLooksAlong() {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromRectangle(0, 0, 10, 10);
        OrthogonalPoint2i corner = new OrthogonalPoint2i(10, 0);

        Integer alongX = ScrollResizeGeometry.cornerEdge(polygon, corner, 1, 0);
        Integer alongZ = ScrollResizeGeometry.cornerEdge(polygon, corner, 0, 1);

        assertNotNull(alongX);
        assertNotNull(alongZ);
        OrthogonalEdge2i xEdge = polygon.edges().get(alongX);
        OrthogonalEdge2i zEdge = polygon.edges().get(alongZ);
        assertEquals(10, xEdge.start().x());
        assertEquals(10, xEdge.end().x());
        assertEquals(0, zEdge.start().z());
        assertEquals(0, zEdge.end().z());
    }

    @Test
    void aMovedCornerIsFoundNearItsOldSpot() {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromRectangle(0, 0, 11, 10);
        assertEquals(new OrthogonalPoint2i(11, 0),
                ScrollResizeGeometry.nearestCorner(polygon, new OrthogonalPoint2i(10, 0), 1));
        assertNull(ScrollResizeGeometry.nearestCorner(polygon, new OrthogonalPoint2i(5, 5), 1));
    }

    @Test
    void shapedSegmentsGrowOutwardAndShrinkInward() {
        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        Claim claim = new Claim(
            new Location(null, 0, 60, 0),
            new Location(null, 20, 60, 20),
            UUID.fromString("7d0e4c2a-1b3f-4a5e-9c8d-6f2a1b3c4d5e"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            1L
        );
        OrthogonalPolygon lShape = OrthogonalPolygon.fromClosedPath(Arrays.asList(
            new OrthogonalPoint2i(0, 0),
            new OrthogonalPoint2i(8, 0),
            new OrthogonalPoint2i(8, 12),
            new OrthogonalPoint2i(20, 12),
            new OrthogonalPoint2i(20, 20),
            new OrthogonalPoint2i(0, 20),
            new OrthogonalPoint2i(0, 0)
        ));
        claim.setShapedCorners(lShape.corners());

        int eastEdge = -1;
        for (int i = 0; i < lShape.edges().size(); i++) {
            OrthogonalEdge2i edge = lShape.edges().get(i);
            if (edge.isVertical() && edge.start().x() == 20) eastEdge = i;
        }

        OrthogonalPolygon grown = plugin.expandShapedSegment(claim, lShape, eastEdge, 1);
        OrthogonalPolygon shrunk = plugin.expandShapedSegment(claim, lShape, eastEdge, -1);

        assertNotNull(grown);
        assertNotNull(shrunk);
        assertEquals(21, grown.maxX());
        assertEquals(19, shrunk.maxX());
        assertEquals(0, grown.minX());
        assertEquals(0, shrunk.minX());
        assertArrayEquals(new int[] {1, 0}, ScrollResizeGeometry.outwardNormal(lShape, eastEdge),
                "the normal used to flip scrolling must match the direction the editor grows the edge");
    }

    @Test
    void aSingleBlockCornerReachesEverySide() {
        int[] bounds = {5, 70, 5, 5, 70, 5};
        assertEquals(ScrollResizeGeometry.allFaces(true), ScrollResizeGeometry.cornerFaces(bounds, 5, 70, 5, true));
    }

    @Test
    void aOneBlockThickClaimKeepsBothSidesOfThatAxisAtItsCorners() {
        int[] bounds = {3, 70, 0, 3, 80, 10};
        assertEquals(EnumSet.of(Face.WEST, Face.EAST, Face.NORTH, Face.UP),
                ScrollResizeGeometry.cornerFaces(bounds, 3, 80, 0, true));
    }

    @Test
    void scrollingUpPushesTheSideAwayFromTheCamera() {
        // Facing north at the north side: scrolling up grows it.
        assertEquals(1, ScrollResizeGeometry.directedStep(1, ScrollResizeGeometry.facing(Face.NORTH, 0, 0, -1)));
        // Facing south at the north side: scrolling up pushes it south, into the claim, and down pulls it out.
        assertEquals(-1, ScrollResizeGeometry.directedStep(1, ScrollResizeGeometry.facing(Face.NORTH, 0, 0, 1)));
        assertEquals(1, ScrollResizeGeometry.directedStep(-1, ScrollResizeGeometry.facing(Face.NORTH, 0, 0, 1)));
        // Looking down at the top of a 3D claim: scrolling up pushes the top down.
        assertEquals(-1, ScrollResizeGeometry.directedStep(1, ScrollResizeGeometry.facing(Face.UP, 0, -0.9, 0.1)));
    }

    @Test
    void shapedEdgesPointOutwardFromTheClaim() {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromRectangle(0, 0, 10, 10);
        for (int i = 0; i < polygon.edges().size(); i++) {
            OrthogonalEdge2i edge = polygon.edges().get(i);
            int[] outward = ScrollResizeGeometry.outwardNormal(polygon, i);
            if (edge.isVertical() && edge.start().x() == 10) assertArrayEquals(new int[] {1, 0}, outward);
            if (edge.isVertical() && edge.start().x() == 0) assertArrayEquals(new int[] {-1, 0}, outward);
            if (edge.isHorizontal() && edge.start().z() == 0) assertArrayEquals(new int[] {0, -1}, outward);
            if (edge.isHorizontal() && edge.start().z() == 10) assertArrayEquals(new int[] {0, 1}, outward);
        }
    }

    @Test
    void lookingDownOnASingleBlockTargetsItsTop() {
        int[] bounds = {5, 70, 5, 5, 70, 5};
        // Eyes two blocks above the block, looking almost straight down at it.
        Face face = ScrollResizeGeometry.rayFace(5.5, 72.6, 5.5, 0.05, -0.99, 0.05, bounds, true);

        assertEquals(Face.UP, face);
    }

    @Test
    void scrollingTowardYouRaisesTheTopAndAwayLowersIt() {
        int[] single = {5, 70, 5, 5, 70, 5};
        double facing = ScrollResizeGeometry.facing(Face.UP, 0.05, -0.99, 0.05);

        // Scroll down pulls the top toward the camera, so the subdivision grows upward.
        int[] taller = ScrollResizeGeometry.step(single, Face.UP, ScrollResizeGeometry.directedStep(-1, facing));
        assertArrayEquals(new int[] {5, 70, 5, 5, 71, 5}, taller);

        // Scroll up pushes it back down to a single layer, and no further.
        int[] back = ScrollResizeGeometry.step(taller, Face.UP, ScrollResizeGeometry.directedStep(1, facing));
        assertArrayEquals(single, back);
        assertNull(ScrollResizeGeometry.step(back, Face.UP, ScrollResizeGeometry.directedStep(1, facing)));
    }

    @Test
    void insideAClaimTheRayTargetsTheSideAhead() {
        int[] bounds = {0, 60, 0, 10, 70, 10};
        assertEquals(Face.NORTH, ScrollResizeGeometry.rayFace(5.5, 65.6, 5.5, 0, 0, -1, bounds, true));
        assertEquals(Face.UP, ScrollResizeGeometry.rayFace(5.5, 65.6, 5.5, 0.1, 0.99, 0, bounds, true));
    }

    @Test
    void theRayIgnoresTheHeightOf2DClaimsAndMissesOutsideClaims() {
        int[] bounds = {0, 60, 0, 10, 60, 10};
        // Straight down inside a 2D claim runs along its height: no wall is targeted.
        assertNull(ScrollResizeGeometry.rayFace(5.5, 65.6, 5.5, 0, -1, 0, bounds, false));
        // Looking away from the claim misses it.
        assertNull(ScrollResizeGeometry.rayFace(20.5, 65.6, 5.5, 1, 0, 0, bounds, false));
        // Looking at its east wall from outside targets that wall.
        assertEquals(Face.EAST, ScrollResizeGeometry.rayFace(20.5, 65.6, 5.5, -1, 0, 0, bounds, false));
    }

    @Test
    void oneBlockWideSubdivisionsFitInsideTheirParent() {
        Claim parent = rectangle(20L, 0, 60, 0, 20, 60, 20);

        // A 1x1 footprint and a 1x2 footprint aren't valid outline polygons; checking them must not throw.
        Claim single = rectangle(21L, 5, 70, 5, 5, 70, 5);
        Claim oneByTwo = rectangle(22L, 5, 70, 5, 5, 71, 6);

        assertEquals(4, ScrollResizeHandler.footprintCorners(single).size());
        org.junit.jupiter.api.Assertions.assertTrue(ScrollResizeHandler.containsFootprint(parent, single));
        org.junit.jupiter.api.Assertions.assertTrue(ScrollResizeHandler.containsFootprint(parent, oneByTwo));

        Claim outside = rectangle(23L, 25, 70, 5, 25, 70, 5);
        org.junit.jupiter.api.Assertions.assertFalse(ScrollResizeHandler.containsFootprint(parent, outside));
    }

    private static Claim rectangle(long id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Claim(
            new Location(null, minX, minY, minZ),
            new Location(null, maxX, maxY, maxZ),
            UUID.fromString("3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }

    @Test
    void boundsIndexesAreOrderedMinThenMax() {
        assertEquals(0, MIN_X);
        assertEquals(3, MAX_X);
    }
}
