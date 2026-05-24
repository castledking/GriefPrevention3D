package me.ryanhamshire.GriefPrevention;

import org.jetbrains.annotations.NotNull;

final class ClaimToolInteractionState
{
    private ClaimToolInteractionState()
    {
    }

    static boolean shouldAttempt3DCornerSelection(@NotNull PlayerData playerData)
    {
        if (playerData.claimResizing != null
                || playerData.claimSubdividing != null
                || playerData.lastShovelLocation != null)
        {
            return false;
        }

        return playerData.shovelMode == ShovelMode.Basic
                || playerData.shovelMode == ShovelMode.Admin
                || playerData.shovelMode == ShovelMode.Admin3D
                || playerData.shovelMode == ShovelMode.Subdivide
                || playerData.shovelMode == ShovelMode.Subdivide3D;
    }
}
