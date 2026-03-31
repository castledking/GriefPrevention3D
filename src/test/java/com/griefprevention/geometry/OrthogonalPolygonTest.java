package com.griefprevention.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrthogonalPolygonTest
{
    @Test
    void validatesRectangle()
    {
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        assertTrue(result.isValid());
        assertNotNull(result.polygon());
        assertEquals(4, result.polygon().corners().size());
        assertEquals(0, result.polygon().minX());
        assertEquals(4, result.polygon().maxX());
        assertEquals(0, result.polygon().minZ());
        assertEquals(3, result.polygon().maxZ());
    }

    @Test
    void rejectsOpenPath()
    {
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.NOT_CLOSED));
    }

    @Test
    void rejectsDiagonalEdge()
    {
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 2),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
        ));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.NON_ORTHOGONAL_EDGE));
    }

    @Test
    void rejectsRepeatedCornerBeforeClosure()
    {
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.DUPLICATE_CORNER));
    }

    @Test
    void rejectsSelfIntersection()
    {
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(6, 0),
                new OrthogonalPoint2i(6, 6),
                new OrthogonalPoint2i(2, 6),
                new OrthogonalPoint2i(2, 2),
                new OrthogonalPoint2i(4, 2),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
        ));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION));
    }

    @Test
    void insertsNodeOnExistingEdge()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygon updated = polygon.insertNode(0, new OrthogonalPoint2i(2, 0));

        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(2, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), updated.corners());
    }

    @Test
    void rejectsNodeAtCorner()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        assertThrows(IllegalArgumentException.class, () -> polygon.insertNode(0, new OrthogonalPoint2i(0, 0)));
    }

    @Test
    void expandsSelectedEdge()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.expandEdge(1, 2);

        assertTrue(result.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(1, 2),
                new OrthogonalPoint2i(3, 2),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.polygon().corners());
    }

    @Test
    void repeatedExpansionCompressesOldTipCornersIntoSidePoints()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygon first = polygon.expandEdge(1, 1).polygon();
        OrthogonalPolygonValidationResult second = first.expandEdge(2, 1);

        assertTrue(second.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(1, 2),
                new OrthogonalPoint2i(3, 2),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), second.polygon().corners());
    }

    @Test
    void rejectsExpansionThatSelfIntersects()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.expandEdge(1, 3);

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION));
    }

    @Test
    void movesCornerAndAdaptsAdjacentSides()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 6),
                new OrthogonalPoint2i(5, 6),
                new OrthogonalPoint2i(5, 8),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.moveCorner(3, new OrthogonalPoint2i(3, 4));

        assertTrue(result.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 4),
                new OrthogonalPoint2i(3, 4),
                new OrthogonalPoint2i(3, 8),
                new OrthogonalPoint2i(0, 8)
        ), result.polygon().corners());
    }

    @Test
    void movingCornerThatSmoothsNibRemovesResolvedSegmentMarkers()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(2, 8),
                new OrthogonalPoint2i(2, 6),
                new OrthogonalPoint2i(4, 6),
                new OrthogonalPoint2i(4, 8),
                new OrthogonalPoint2i(8, 8),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.moveCorner(3, new OrthogonalPoint2i(4, 8));

        assertTrue(result.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(8, 8),
                new OrthogonalPoint2i(8, 0)
        ), result.polygon().corners());
    }

    @Test
    void cardinalMoveCornerOntoExistingVertexSmoothsNib()
    {
        // Inner corner (4,4) merged onto (8,4) — pure X move; resize routing used to miss exact corner targets.
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 4),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(4, 8),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.moveCorner(3, new OrthogonalPoint2i(8, 4));

        assertTrue(result.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 8),
                new OrthogonalPoint2i(0, 8)
        ), result.polygon().corners());
    }

    @Test
    void movingWholeSegmentedFaceResolvesMarkerIntoCleanSide()
    {
        OrthogonalPolygon polygon = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(2, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3),
                new OrthogonalPoint2i(0, 0)
        ));

        OrthogonalPolygonValidationResult result = polygon.moveEdgeRun(0, 1, 1);

        assertTrue(result.isValid());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 1),
                new OrthogonalPoint2i(4, 1),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.polygon().corners());
    }
}
