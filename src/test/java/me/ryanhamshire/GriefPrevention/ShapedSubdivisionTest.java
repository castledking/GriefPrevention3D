package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ShapedSubdivisionTest {

    private static final UUID OWNER = UUID.fromString("b2b2f3d0-5bd9-4a54-9f52-4a0a8f7a9d10");

    @Test
    void aSubdivisionWithShapedCornersReportsAsShaped() {
        Claim parent = claim(1L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 2L, 0, 0, 10, 10);
        subdivision.setShapedCorners(lShape());

        assertTrue(subdivision.isShaped());
        assertTrue(subdivision.isShapedSubdivision());
        assertFalse(parent.isShapedSubdivision());
    }

    @Test
    void aRectangularSubdivisionIsNotShaped() {
        Claim parent = claim(3L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 4L, 2, 2, 6, 6);

        assertFalse(subdivision.isShaped());
        assertFalse(subdivision.isShapedSubdivision());
    }

    @Test
    void a3DSubdivisionNeverBecomesShaped() {
        Claim parent = claim(5L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 6L, 0, 0, 10, 10);
        subdivision.set3D(true);
        subdivision.setShapedCorners(lShape());

        assertFalse(subdivision.isShaped());
        assertFalse(subdivision.isShapedSubdivision());
    }

    @Test
    void containmentFollowsTheShapedOutlineRatherThanTheBoundingBox() {
        Claim parent = claim(7L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 8L, 0, 0, 10, 10);
        subdivision.setShapedCorners(lShape());

        // Inside the L's vertical leg.
        assertTrue(subdivision.contains(new Location(null, 1, 64, 1), true, false));
        // Inside the L's horizontal foot.
        assertTrue(subdivision.contains(new Location(null, 8, 64, 8), true, false));
        // The notch the L cuts out, which a bounding box would have swallowed.
        assertFalse(subdivision.contains(new Location(null, 8, 64, 1), true, false));
    }

    @Test
    void theBoundaryPolygonSurvivesACopy() {
        Claim parent = claim(9L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 10L, 0, 0, 10, 10);
        subdivision.setShapedCorners(lShape());

        Claim copy = new Claim(subdivision);

        assertTrue(copy.isShaped());
        assertEquals(subdivision.getBoundaryPolygon().corners(), copy.getBoundaryPolygon().corners());
    }

    @Test
    void cornerLookupUsesTheShapedCorners() {
        Claim parent = claim(11L, OWNER, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 12L, 0, 0, 10, 10);
        subdivision.setShapedCorners(lShape());

        OrthogonalPolygon polygon = subdivision.getBoundaryPolygon();
        OrthogonalPoint2i concaveCorner = new OrthogonalPoint2i(4, 4);

        assertTrue(polygon.corners().contains(concaveCorner));
        assertTrue(subdivision.getCornerIndexAt(concaveCorner.x(), concaveCorner.z()) >= 0);
        // A point on an edge is not a corner.
        assertEquals(-1, subdivision.getCornerIndexAt(2, 0));
    }

    /** An L covering x 0..4 for all z, plus z 4..10 for all x. */
    private static List<OrthogonalPoint2i> lShape() {
        return Arrays.asList(
            new OrthogonalPoint2i(0, 0),
            new OrthogonalPoint2i(4, 0),
            new OrthogonalPoint2i(4, 4),
            new OrthogonalPoint2i(10, 4),
            new OrthogonalPoint2i(10, 10),
            new OrthogonalPoint2i(0, 10)
        );
    }

    private static Claim claim(long id, UUID owner, int minX, int minZ, int maxX, int maxZ) {
        return new Claim(
            new Location(null, minX, 64, minZ),
            new Location(null, maxX, 64, maxZ),
            owner,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }

    private static Claim subdivision(Claim parent, long id, int minX, int minZ, int maxX, int maxZ) {
        Claim child = claim(id, null, minX, minZ, maxX, maxZ);
        child.parent = parent;
        parent.children.add(child);
        return child;
    }
}
