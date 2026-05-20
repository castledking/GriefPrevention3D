package com.griefprevention.compat;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldHeightCompatProviderTest {

    @Test
    void current_returnsAWorldHeightCompat() {
        assertNotNull(WorldHeightCompatProvider.current());
    }

    @Test
    void legacySelection_usesZeroMinimumAndWorldMaximum() {
        World world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(256);

        WorldHeightCompat compat = WorldHeightCompatProvider.select(false);

        assertEquals(0, compat.minHeight(world));
        assertEquals(256, compat.maxHeight(world));
    }

    @Test
    void modernSelection_usesWorldMinimumAndMaximum() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);

        WorldHeightCompat compat = WorldHeightCompatProvider.select(true);

        assertEquals(-64, compat.minHeight(world));
        assertEquals(320, compat.maxHeight(world));
    }
}
