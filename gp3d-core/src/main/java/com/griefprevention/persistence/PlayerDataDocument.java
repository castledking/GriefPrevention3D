package com.griefprevention.persistence;

import java.util.Objects;

/**
 * The claim-block values stored in GriefPrevention's flat-file {@code PlayerData/<uuid>} record.
 *
 * <p>The first and fourth lines in the upstream format are currently unused. Claim ownership is
 * stored in the claim graph, so creating, resizing, or abandoning a claim does not mutate these
 * two entitlement values when the default full abandon return ratio is in use.</p>
 */
public final class PlayerDataDocument
{
    private final int accruedClaimBlocks;
    private final int bonusClaimBlocks;

    public PlayerDataDocument(int accruedClaimBlocks, int bonusClaimBlocks)
    {
        this.accruedClaimBlocks = accruedClaimBlocks;
        this.bonusClaimBlocks = bonusClaimBlocks;
    }

    public int accruedClaimBlocks()
    {
        return this.accruedClaimBlocks;
    }

    public int bonusClaimBlocks()
    {
        return this.bonusClaimBlocks;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof PlayerDataDocument)) return false;
        PlayerDataDocument that = (PlayerDataDocument) other;
        return this.accruedClaimBlocks == that.accruedClaimBlocks
                && this.bonusClaimBlocks == that.bonusClaimBlocks;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.accruedClaimBlocks, this.bonusClaimBlocks);
    }

    @Override
    public String toString()
    {
        return "PlayerDataDocument[accruedClaimBlocks=" + this.accruedClaimBlocks
                + ", bonusClaimBlocks=" + this.bonusClaimBlocks + "]";
    }
}
