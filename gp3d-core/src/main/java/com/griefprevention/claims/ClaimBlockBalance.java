package com.griefprevention.claims;

/** A derived view of a player's claim-block entitlements and top-level claimed area. */
public final class ClaimBlockBalance
{
    private final int totalEntitlement;
    private final int claimedArea;
    private final int remaining;

    ClaimBlockBalance(int totalEntitlement, int claimedArea, int remaining)
    {
        this.totalEntitlement = totalEntitlement;
        this.claimedArea = claimedArea;
        this.remaining = remaining;
    }

    public int totalEntitlement()
    {
        return this.totalEntitlement;
    }

    public int claimedArea()
    {
        return this.claimedArea;
    }

    public int remaining()
    {
        return this.remaining;
    }
}
