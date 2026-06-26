package com.griefprevention.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrthogonalEdge2iTest
{
    @Test
    void horizontalEdgeDetected()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(10, 5));

        assertTrue(edge.isHorizontal());
        assertFalse(edge.isVertical());
        assertTrue(edge.isOrthogonal());
    }

    @Test
    void verticalEdgeDetected()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(5, 10));

        assertFalse(edge.isHorizontal());
        assertTrue(edge.isVertical());
        assertTrue(edge.isOrthogonal());
    }

    @Test
    void diagonalEdgeIsNotOrthogonal()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 5));

        assertFalse(edge.isHorizontal());
        assertFalse(edge.isVertical());
        assertFalse(edge.isOrthogonal());
    }

    @Test
    void zeroLengthEdgeIsNeitherHorizontalNorVertical()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(3, 3),
                new OrthogonalPoint2i(3, 3));

        assertFalse(edge.isHorizontal());
        assertFalse(edge.isVertical());
        assertFalse(edge.isOrthogonal());
    }

    @Test
    void lengthOfHorizontalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(2, 5),
                new OrthogonalPoint2i(8, 5));

        assertEquals(6, edge.length());
    }

    @Test
    void lengthOfVerticalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(5, 1),
                new OrthogonalPoint2i(5, 9));

        assertEquals(8, edge.length());
    }

    @Test
    void minMaxCoordinates()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(8, 3),
                new OrthogonalPoint2i(2, 7));

        assertEquals(2, edge.minX());
        assertEquals(8, edge.maxX());
        assertEquals(3, edge.minZ());
        assertEquals(7, edge.maxZ());
    }

    @Test
    void containsPointOnHorizontalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(10, 5));

        assertTrue(edge.containsPoint(new OrthogonalPoint2i(5, 5)));
        assertTrue(edge.containsPoint(new OrthogonalPoint2i(0, 5)));
        assertTrue(edge.containsPoint(new OrthogonalPoint2i(10, 5)));
        assertFalse(edge.containsPoint(new OrthogonalPoint2i(5, 6)));
        assertFalse(edge.containsPoint(new OrthogonalPoint2i(11, 5)));
    }

    @Test
    void containsPointOnVerticalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(5, 10));

        assertTrue(edge.containsPoint(new OrthogonalPoint2i(5, 5)));
        assertTrue(edge.containsPoint(new OrthogonalPoint2i(5, 0)));
        assertTrue(edge.containsPoint(new OrthogonalPoint2i(5, 10)));
        assertFalse(edge.containsPoint(new OrthogonalPoint2i(4, 5)));
        assertFalse(edge.containsPoint(new OrthogonalPoint2i(5, 11)));
    }

    @Test
    void containsInteriorPointExcludesEndpoints()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(10, 5));

        assertTrue(edge.containsInteriorPoint(new OrthogonalPoint2i(5, 5)));
        assertFalse(edge.containsInteriorPoint(new OrthogonalPoint2i(0, 5)));
        assertFalse(edge.containsInteriorPoint(new OrthogonalPoint2i(10, 5)));
    }

    @Test
    void outwardDirectionForHorizontalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 5),
                new OrthogonalPoint2i(10, 5));

        assertEquals(OrthogonalDirection.SOUTH, edge.outwardDirectionForPositiveOffset());
    }

    @Test
    void outwardDirectionForVerticalEdge()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(5, 0),
                new OrthogonalPoint2i(5, 10));

        assertEquals(OrthogonalDirection.EAST, edge.outwardDirectionForPositiveOffset());
    }

    @Test
    void outwardDirectionThrowsForDiagonal()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 5));

        assertThrows(IllegalStateException.class, edge::outwardDirectionForPositiveOffset);
    }

    @Test
    void startAndEndAccessors()
    {
        OrthogonalPoint2i start = new OrthogonalPoint2i(1, 2);
        OrthogonalPoint2i end = new OrthogonalPoint2i(3, 4);
        OrthogonalEdge2i edge = new OrthogonalEdge2i(start, end);

        assertEquals(start, edge.start());
        assertEquals(end, edge.end());
    }

    @Test
    void equalsAndHashCode()
    {
        OrthogonalEdge2i e1 = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 0));
        OrthogonalEdge2i e2 = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(5, 0));
        OrthogonalEdge2i e3 = new OrthogonalEdge2i(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(0, 5));

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
    }

    @Test
    void toStringContainsPoints()
    {
        OrthogonalEdge2i edge = new OrthogonalEdge2i(
                new OrthogonalPoint2i(1, 2),
                new OrthogonalPoint2i(3, 4));

        String str = edge.toString();
        assertTrue(str.contains("OrthogonalEdge2i"));
    }
}
