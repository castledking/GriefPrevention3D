package com.griefprevention.claims;

/** Applies Bukkit's configured claim-block loss when a top-level claim is abandoned. */
public final class ClaimBlockAbandonment
{
    private ClaimBlockAbandonment()
    {
    }

    public static int accruedPenalty(int claimArea, double returnRatio)
    {
        return (int) Math.ceil(claimArea * (1.0D - returnRatio));
    }

    public static int accruedAfterAbandonment(
            int accruedClaimBlocks,
            int claimArea,
            double returnRatio)
    {
        // Ordinary int subtraction intentionally matches Bukkit, including overflow behavior.
        return accruedClaimBlocks - accruedPenalty(claimArea, returnRatio);
    }
}
