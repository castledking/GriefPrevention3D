package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

/** {@code GriefPrevention.Economy}: buying and selling claim blocks. */
public final class EconomySettings
{
    public static final boolean DEFAULT_CLAIM_BLOCKS_ENABLED = false;
    public static final int DEFAULT_CLAIM_BLOCKS_MAX_BONUS = 0;
    public static final double DEFAULT_CLAIM_BLOCKS_PURCHASE_COST = 1.0D;
    public static final double DEFAULT_CLAIM_BLOCKS_SELL_VALUE = 0.5D;

    private final boolean claimBlocksEnabled;
    private final int claimBlocksMaxBonus;
    private final double claimBlocksPurchaseCost;
    private final double claimBlocksSellValue;

    public EconomySettings(
            boolean claimBlocksEnabled,
            int claimBlocksMaxBonus,
            double claimBlocksPurchaseCost,
            double claimBlocksSellValue)
    {
        this.claimBlocksEnabled = claimBlocksEnabled;
        this.claimBlocksMaxBonus = claimBlocksMaxBonus;
        this.claimBlocksPurchaseCost = claimBlocksPurchaseCost;
        this.claimBlocksSellValue = claimBlocksSellValue;
    }

    public static @NotNull EconomySettings upstreamDefaults()
    {
        return new EconomySettings(
                DEFAULT_CLAIM_BLOCKS_ENABLED,
                DEFAULT_CLAIM_BLOCKS_MAX_BONUS,
                DEFAULT_CLAIM_BLOCKS_PURCHASE_COST,
                DEFAULT_CLAIM_BLOCKS_SELL_VALUE
        );
    }

    public boolean claimBlocksEnabled()
    {
        return this.claimBlocksEnabled;
    }

    public int claimBlocksMaxBonus()
    {
        return this.claimBlocksMaxBonus;
    }

    public double claimBlocksPurchaseCost()
    {
        return this.claimBlocksPurchaseCost;
    }

    public double claimBlocksSellValue()
    {
        return this.claimBlocksSellValue;
    }
}
