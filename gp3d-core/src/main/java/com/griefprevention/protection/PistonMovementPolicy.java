package com.griefprevention.protection;

import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/** Paper's piston rules, which stop pistons pushing or pulling blocks across claim borders. */
public final class PistonMovementPolicy
{
    private PistonMovementPolicy()
    {
    }

    /**
     * @param worldKey the world the piston is in
     * @param pistonClaim the most specific claim containing the piston, or null
     * @param affected every block the move touches: each moved block, where it lands, and each block
     *                 the move breaks
     * @param invaded where an extending piston's head goes when it moves no blocks, otherwise null
     */
    public static boolean mayMove(
            @NotNull PistonMode mode,
            @NotNull String worldKey,
            @Nullable ClaimSnapshot pistonClaim,
            @NotNull Collection<BlockPoint> affected,
            @Nullable BlockPoint invaded,
            @NotNull ClaimLookup claims)
    {
        if (mode == PistonMode.IGNORED)
        {
            return true;
        }
        if (pistonClaim == null && mode == PistonMode.CLAIMS_ONLY)
        {
            return false;
        }

        if (affected.isEmpty())
        {
            if (invaded == null)
            {
                return true;
            }
            ClaimSnapshot invadedClaim = claims.claimAt(invaded);
            return invadedClaim == null || ClaimRelations.sameTree(pistonClaim, invadedClaim, claims::claimById);
        }

        if (pistonClaim != null)
        {
            boolean allInside = true;
            for (BlockPoint point : affected)
            {
                if (!pistonClaim.contains(worldKey, point.x(), point.y(), point.z(), false))
                {
                    allInside = false;
                    break;
                }
            }
            if (allInside)
            {
                return true;
            }
            // Pushing ice out and melting it, for one, would put water outside the claim.
            if (mode == PistonMode.CLAIMS_ONLY)
            {
                return false;
            }
        }

        for (BlockPoint point : affected)
        {
            ClaimSnapshot claim = claims.claimAt(point);
            if (claim != null && !ClaimRelations.sameTree(pistonClaim, claim, claims::claimById))
            {
                return false;
            }
        }
        return true;
    }
}
