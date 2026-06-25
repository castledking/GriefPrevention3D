package com.griefprevention.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntVectorTest
{
    @Test
    void constructorSetsCoordinates()
    {
        IntVector v = new IntVector(1, 2, 3);
        assertEquals(1, v.x());
        assertEquals(2, v.y());
        assertEquals(3, v.z());
    }

    @Test
    void constructFromBlock()
    {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(20);
        when(block.getZ()).thenReturn(30);

        IntVector v = new IntVector(block);

        assertEquals(10, v.x());
        assertEquals(20, v.y());
        assertEquals(30, v.z());
    }

    @Test
    void constructFromLocation()
    {
        Location loc = mock(Location.class);
        when(loc.getBlockX()).thenReturn(5);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(-10);

        IntVector v = new IntVector(loc);

        assertEquals(5, v.x());
        assertEquals(64, v.y());
        assertEquals(-10, v.z());
    }

    @Test
    void constructFromVector()
    {
        Vector vec = new Vector(3.7, 8.2, -1.5);

        IntVector v = new IntVector(vec);

        assertEquals(3, v.x());
        assertEquals(8, v.y());
        assertEquals(-2, v.z());
    }

    @Test
    void addWithIntegers()
    {
        IntVector v = new IntVector(1, 2, 3);
        IntVector result = v.add(10, 20, 30);

        assertEquals(11, result.x());
        assertEquals(22, result.y());
        assertEquals(33, result.z());
    }

    @Test
    void addWithIntVector()
    {
        IntVector v1 = new IntVector(1, 2, 3);
        IntVector v2 = new IntVector(4, 5, 6);
        IntVector result = v1.add(v2);

        assertEquals(5, result.x());
        assertEquals(7, result.y());
        assertEquals(9, result.z());
    }

    @Test
    void addDoesNotMutateOriginal()
    {
        IntVector v = new IntVector(1, 2, 3);
        v.add(10, 20, 30);

        assertEquals(1, v.x());
        assertEquals(2, v.y());
        assertEquals(3, v.z());
    }

    @Test
    void distanceSquaredToSelf()
    {
        IntVector v = new IntVector(5, 10, 15);
        assertEquals(0, v.distanceSquared(v));
    }

    @Test
    void distanceSquaredToOther()
    {
        IntVector v1 = new IntVector(0, 0, 0);
        IntVector v2 = new IntVector(3, 4, 0);

        assertEquals(25, v1.distanceSquared(v2));
    }

    @Test
    void distanceSquaredIsSymmetric()
    {
        IntVector v1 = new IntVector(1, 2, 3);
        IntVector v2 = new IntVector(4, 6, 8);

        assertEquals(v1.distanceSquared(v2), v2.distanceSquared(v1));
    }

    @Test
    void toLocationCreatesNewLocation()
    {
        IntVector v = new IntVector(10, 64, -5);
        Location loc = v.toLocation(null);

        assertEquals(10, loc.getBlockX());
        assertEquals(64, loc.getBlockY());
        assertEquals(-5, loc.getBlockZ());
    }

    @Test
    void toVectorCreatesBlockVector()
    {
        IntVector v = new IntVector(7, 8, 9);
        BlockVector bv = v.toVector();

        assertEquals(7, bv.getBlockX());
        assertEquals(8, bv.getBlockY());
        assertEquals(9, bv.getBlockZ());
    }

    @Test
    void toBlockDelegatesToWorld()
    {
        World world = mock(World.class);
        Block mockBlock = mock(Block.class);
        when(world.getBlockAt(3, 4, 5)).thenReturn(mockBlock);

        IntVector v = new IntVector(3, 4, 5);
        Block block = v.toBlock(world);

        assertEquals(mockBlock, block);
        verify(world).getBlockAt(3, 4, 5);
    }

    @Test
    void isChunkLoadedDelegatesToWorld()
    {
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);

        IntVector v = new IntVector(8, 64, 12);
        assertTrue(v.isChunkLoaded(world));
    }

    @Test
    void isChunkLoadedUsesCorrectChunkCoords()
    {
        World world = mock(World.class);
        when(world.isChunkLoaded(1, 2)).thenReturn(false);

        IntVector v = new IntVector(16, 64, 32);
        assertFalse(v.isChunkLoaded(world));
        verify(world).isChunkLoaded(1, 2);
    }

    @Test
    void equalsAndHashCode()
    {
        IntVector v1 = new IntVector(1, 2, 3);
        IntVector v2 = new IntVector(1, 2, 3);
        IntVector v3 = new IntVector(4, 5, 6);

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertNotEquals(v1, v3);
    }

    @Test
    void equalsReturnsFalseForDifferentType()
    {
        IntVector v = new IntVector(1, 2, 3);
        assertNotEquals(v, "not a vector");
    }

    @Test
    void equalsReturnsTrueForSameInstance()
    {
        IntVector v = new IntVector(1, 2, 3);
        assertEquals(v, v);
    }

    @Test
    void toStringIncludesCoordinates()
    {
        IntVector v = new IntVector(10, 20, 30);
        String str = v.toString();

        assertTrue(str.contains("10"));
        assertTrue(str.contains("20"));
        assertTrue(str.contains("30"));
    }

    @Test
    void negativeCoordinates()
    {
        IntVector v = new IntVector(-5, -10, -15);
        assertEquals(-5, v.x());
        assertEquals(-10, v.y());
        assertEquals(-15, v.z());

        IntVector added = v.add(5, 10, 15);
        assertEquals(0, added.x());
        assertEquals(0, added.y());
        assertEquals(0, added.z());
    }
}
