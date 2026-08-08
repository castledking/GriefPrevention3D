package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Applies upstream claim-block arithmetic to the shared claim graph.
 *
 * <p>Accrued, personal bonus, and permission-group bonus blocks are entitlements. Remaining blocks
 * are derived by subtracting the exact area of the player's top-level claims. Subdivisions consume
 * no additional blocks. Overflow behavior intentionally matches Bukkit's {@code PlayerData}.</p>
 */
public final class PlayerClaimBlockAccount
{
    private final @NotNull UUID ownerId;
    private final int accruedClaimBlocks;
    private final int bonusClaimBlocks;
    private final int groupBonusClaimBlocks;

    public PlayerClaimBlockAccount(
            @NotNull UUID ownerId,
            int accruedClaimBlocks,
            int bonusClaimBlocks,
            int groupBonusClaimBlocks)
    {
        this.ownerId = ownerId;
        this.accruedClaimBlocks = accruedClaimBlocks;
        this.bonusClaimBlocks = bonusClaimBlocks;
        this.groupBonusClaimBlocks = groupBonusClaimBlocks;
    }

    public @NotNull ClaimBlockBalance balance(@NotNull Iterable<ClaimSnapshot> claims)
    {
        int totalEntitlement;
        try
        {
            totalEntitlement = Math.addExact(
                    Math.addExact(this.accruedClaimBlocks, this.bonusClaimBlocks),
                    this.groupBonusClaimBlocks
            );
        }
        catch (ArithmeticException exception)
        {
            totalEntitlement = Integer.MAX_VALUE;
        }

        long claimedArea = 0L;
        int remaining = totalEntitlement;
        try
        {
            for (ClaimSnapshot claim : claims)
            {
                if (!this.ownerId.equals(claim.ownerId())
                        || claim.parentId() != null
                        || claim.subdivision())
                {
                    continue;
                }

                int area = claim.bounds().area();
                claimedArea = Math.min((long) Integer.MAX_VALUE, claimedArea + (long) area);
                remaining = Math.subtractExact(remaining, area);
            }
        }
        catch (ArithmeticException exception)
        {
            return new ClaimBlockBalance(totalEntitlement, (int) claimedArea, 0);
        }

        return new ClaimBlockBalance(totalEntitlement, (int) claimedArea, remaining);
    }
}
