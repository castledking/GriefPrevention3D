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
        return decide(mode, worldKey, pistonClaim, affected, invaded, claims).allowed();
    }

    /** {@link #mayMove}, also saying which claim a refused move would have violated, for its owner's alert. */
    public static @NotNull Decision decide(
            @NotNull PistonMode mode,
            @NotNull String worldKey,
            @Nullable ClaimSnapshot pistonClaim,
            @NotNull Collection<BlockPoint> affected,
            @Nullable BlockPoint invaded,
            @NotNull ClaimLookup claims)
    {
        if (mode == PistonMode.IGNORED)
        {
            return Decision.ALLOWED;
        }
        if (pistonClaim == null && mode == PistonMode.CLAIMS_ONLY)
        {
            return Decision.REFUSED;
        }

        if (affected.isEmpty())
        {
            if (invaded == null)
            {
                return Decision.ALLOWED;
            }
            ClaimSnapshot invadedClaim = claims.claimAt(invaded);
            if (invadedClaim == null || ClaimRelations.sameTree(pistonClaim, invadedClaim, claims::claimById))
            {
                return Decision.ALLOWED;
            }
            return new Decision(Violation.HEAD_ENTERED, invadedClaim);
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
                return Decision.ALLOWED;
            }
            // Pushing ice out and melting it, for one, would put water outside the claim.
            if (mode == PistonMode.CLAIMS_ONLY)
            {
                return new Decision(Violation.LEFT_CLAIM, pistonClaim);
            }
        }

        for (BlockPoint point : affected)
        {
            ClaimSnapshot claim = claims.claimAt(point);
            if (claim != null && !ClaimRelations.sameTree(pistonClaim, claim, claims::claimById))
            {
                return new Decision(Violation.ENTERED_CLAIM, claim);
            }
        }
        return Decision.ALLOWED;
    }

    /** How a refused move broke the rules, which decides whose alert it is and where it points. */
    public enum Violation
    {
        /** A piston head with nothing in front of it reached into another claim. */
        HEAD_ENTERED,
        /** The move would carry blocks out of the piston's own claim. */
        LEFT_CLAIM,
        /** The move would carry blocks into another claim. */
        ENTERED_CLAIM
    }

    /** Whether a move may happen, and if not, which claim it would have violated. */
    public static final class Decision
    {
        private static final Decision ALLOWED = new Decision(null, null);
        /** Refused without a claim to tell, such as a wilderness piston where pistons need claims. */
        private static final Decision REFUSED = new Decision(null, null);

        private final @Nullable Violation violation;
        private final @Nullable ClaimSnapshot violatedClaim;

        private Decision(@Nullable Violation violation, @Nullable ClaimSnapshot violatedClaim)
        {
            this.violation = violation;
            this.violatedClaim = violatedClaim;
        }

        public boolean allowed()
        {
            return this == ALLOWED;
        }

        /** @return how the move broke the rules, or null when it was allowed or concerns no claim */
        public @Nullable Violation violation()
        {
            return this.violation;
        }

        /** @return the claim whose owner hears about the refused move, or null */
        public @Nullable ClaimSnapshot violatedClaim()
        {
            return this.violatedClaim;
        }
    }
}
