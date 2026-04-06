package com.griefprevention.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimCreateLayoutTest
{
    @Test
    void centersEvenWidthAroundPlayerLocation()
    {
        assertEquals(96, ClaimCreateLayout.centeredAxisStart(100.5D, 10));
        assertEquals(105, ClaimCreateLayout.fromExactSideLength(new org.bukkit.Location(null, 100.5D, 64D, 200.5D), 10).greaterX());
        assertEquals(205, ClaimCreateLayout.fromExactSideLength(new org.bukkit.Location(null, 100.5D, 64D, 200.5D), 10).greaterZ());
    }

    @Test
    void centersOddWidthAroundPlayerLocation()
    {
        assertEquals(95, ClaimCreateLayout.centeredAxisStart(100.5D, 11));
        assertEquals(105, ClaimCreateLayout.fromExactSideLength(new org.bukkit.Location(null, 100.5D, 64D, 200.5D), 11).greaterX());
        assertEquals(205, ClaimCreateLayout.fromExactSideLength(new org.bukkit.Location(null, 100.5D, 64D, 200.5D), 11).greaterZ());
    }
}
