package com.griefprevention.fabric;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapedBoundaryMarkersTest
{
    @Test
    void polygonEdgesHaveSideMarkersBesideEveryCorner()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromRectangle(0, 0, 8, 6);

        assertEquals(Set.of(
                new OrthogonalPoint2i(1, 0), new OrthogonalPoint2i(7, 0),
                new OrthogonalPoint2i(8, 1), new OrthogonalPoint2i(8, 5),
                new OrthogonalPoint2i(7, 6), new OrthogonalPoint2i(1, 6),
                new OrthogonalPoint2i(0, 5), new OrthogonalPoint2i(0, 1)),
                Set.copyOf(ShapedBoundaryMarkers.cornerNibs(polygon)));
    }

    @Test
    void openPathShowsNibsOnlyAlongSegmentsAlreadyTraced()
    {
        List<OrthogonalPoint2i> path = List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(5, 4));

        List<OrthogonalPoint2i> nibs = ShapedBoundaryMarkers.cornerNibs(path);
        assertTrue(nibs.contains(new OrthogonalPoint2i(1, 0)));
        assertTrue(nibs.contains(new OrthogonalPoint2i(4, 0)));
        assertTrue(nibs.contains(new OrthogonalPoint2i(5, 1)));
        assertTrue(nibs.contains(new OrthogonalPoint2i(5, 3)));
        assertEquals(4, nibs.size());
    }

    @Test
    void oneBlockEdgesDoNotAddASeparateNib()
    {
        assertTrue(ShapedBoundaryMarkers.cornerNibs(List.of(
                new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(1, 0))).isEmpty());
    }
}
