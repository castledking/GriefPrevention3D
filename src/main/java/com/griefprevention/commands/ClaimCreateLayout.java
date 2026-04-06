package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

final class ClaimCreateLayout
{
    private ClaimCreateLayout()
    {
    }

    record Bounds(int lesserX, int lesserZ, int greaterX, int greaterZ)
    {
    }

    static @NotNull Bounds resolveDefaultBounds(
            @NotNull GriefPrevention plugin,
            @NotNull PlayerData playerData,
            @NotNull Location playerLocation)
    {
        int sideLength = resolveDefaultSideLength(plugin, playerData);
        return fromExactSideLength(playerLocation, sideLength);
    }

    static @NotNull Bounds fromExactSideLength(@NotNull Location playerLocation, int sideLength)
    {
        int span = Math.max(1, sideLength);
        int lesserX = centeredAxisStart(playerLocation.getX(), span);
        int lesserZ = centeredAxisStart(playerLocation.getZ(), span);
        int greaterX = Math.addExact(lesserX, span - 1);
        int greaterZ = Math.addExact(lesserZ, span - 1);
        return new Bounds(lesserX, lesserZ, greaterX, greaterZ);
    }

    static int centeredAxisStart(double center, int span)
    {
        long start = Math.round(center - Math.max(1, span) / 2.0D);
        if (start < Integer.MIN_VALUE || start > Integer.MAX_VALUE)
        {
            throw new ArithmeticException("Claim axis start is out of bounds.");
        }

        return (int) start;
    }

    private static int resolveDefaultSideLength(
            @NotNull GriefPrevention plugin,
            @NotNull PlayerData playerData)
    {
        int sideLength = resolveMinimumSideLength(plugin);
        if (playerData.getClaims().isEmpty() && plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0)
        {
            sideLength = Math.max(sideLength, radiusToDiameter(plugin.config_claims_automaticClaimsForNewPlayersRadius));
        }

        return sideLength;
    }

    private static int resolveMinimumSideLength(@NotNull GriefPrevention plugin)
    {
        int minWidth = Math.max(1, plugin.config_claims_minWidth);
        int minAreaSide = (int) Math.ceil(Math.sqrt(Math.max(1, plugin.config_claims_minArea)));
        return Math.max(minWidth, minAreaSide);
    }

    private static int radiusToDiameter(int radius)
    {
        int clampedRadius = Math.max(0, radius);
        long diameter = clampedRadius * 2L + 1L;
        return diameter > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) diameter;
    }
}
