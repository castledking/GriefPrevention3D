package com.griefprevention.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrthogonalDirectionTest
{
    @Test
    void northOppositeIsSouth()
    {
        assertEquals(OrthogonalDirection.SOUTH, OrthogonalDirection.NORTH.opposite());
    }

    @Test
    void southOppositeIsNorth()
    {
        assertEquals(OrthogonalDirection.NORTH, OrthogonalDirection.SOUTH.opposite());
    }

    @Test
    void eastOppositeIsWest()
    {
        assertEquals(OrthogonalDirection.WEST, OrthogonalDirection.EAST.opposite());
    }

    @Test
    void westOppositeIsEast()
    {
        assertEquals(OrthogonalDirection.EAST, OrthogonalDirection.WEST.opposite());
    }

    @Test
    void oppositeOfOppositeReturnsSelf()
    {
        for (OrthogonalDirection dir : OrthogonalDirection.values())
        {
            assertEquals(dir, dir.opposite().opposite());
        }
    }

    @Test
    void oppositeIsNeverSelf()
    {
        for (OrthogonalDirection dir : OrthogonalDirection.values())
        {
            assertNotEquals(dir, dir.opposite());
        }
    }
}
