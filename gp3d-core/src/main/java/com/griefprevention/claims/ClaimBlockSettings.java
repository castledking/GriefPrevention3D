package com.griefprevention.claims;

/** The shared claim-block configuration currently consumed by native adapters. */
public final class ClaimBlockSettings
{
    public static final int DEFAULT_INITIAL_BLOCKS = 100;
    public static final int DEFAULT_BLOCKS_ACCRUED_PER_HOUR = 100;
    public static final int DEFAULT_MAXIMUM_ACCRUED_CLAIM_BLOCKS = 80000;
    public static final int DEFAULT_ACCRUED_IDLE_THRESHOLD = 0;
    public static final int DEFAULT_ACCRUED_IDLE_PERCENT = 0;
    public static final int DEFAULT_MAXIMUM_CLAIMS_PER_PLAYER = 0;
    public static final double DEFAULT_ABANDON_RETURN_RATIO = 1.0D;

    private final int initialBlocks;
    private final int blocksAccruedPerHour;
    private final int maximumAccruedClaimBlocks;
    private final int accruedIdleThreshold;
    private final int accruedIdlePercent;
    private final int maximumClaimsPerPlayer;
    private final double abandonReturnRatio;

    public ClaimBlockSettings(int initialBlocks)
    {
        this(
                initialBlocks,
                DEFAULT_BLOCKS_ACCRUED_PER_HOUR,
                DEFAULT_MAXIMUM_ACCRUED_CLAIM_BLOCKS,
                DEFAULT_ACCRUED_IDLE_THRESHOLD,
                DEFAULT_ACCRUED_IDLE_PERCENT,
                DEFAULT_MAXIMUM_CLAIMS_PER_PLAYER,
                DEFAULT_ABANDON_RETURN_RATIO
        );
    }

    public ClaimBlockSettings(
            int initialBlocks,
            int maximumClaimsPerPlayer,
            double abandonReturnRatio)
    {
        this(
                initialBlocks,
                DEFAULT_BLOCKS_ACCRUED_PER_HOUR,
                DEFAULT_MAXIMUM_ACCRUED_CLAIM_BLOCKS,
                DEFAULT_ACCRUED_IDLE_THRESHOLD,
                DEFAULT_ACCRUED_IDLE_PERCENT,
                maximumClaimsPerPlayer,
                abandonReturnRatio
        );
    }

    public ClaimBlockSettings(
            int initialBlocks,
            int blocksAccruedPerHour,
            int maximumAccruedClaimBlocks,
            int accruedIdleThreshold,
            int accruedIdlePercent,
            int maximumClaimsPerPlayer,
            double abandonReturnRatio)
    {
        this.initialBlocks = initialBlocks;
        this.blocksAccruedPerHour = blocksAccruedPerHour;
        this.maximumAccruedClaimBlocks = maximumAccruedClaimBlocks;
        this.accruedIdleThreshold = accruedIdleThreshold;
        this.accruedIdlePercent = accruedIdlePercent;
        this.maximumClaimsPerPlayer = maximumClaimsPerPlayer;
        this.abandonReturnRatio = abandonReturnRatio;
    }

    public int initialBlocks()
    {
        return this.initialBlocks;
    }

    public int maximumClaimsPerPlayer()
    {
        return this.maximumClaimsPerPlayer;
    }

    public int blocksAccruedPerHour()
    {
        return this.blocksAccruedPerHour;
    }

    public int maximumAccruedClaimBlocks()
    {
        return this.maximumAccruedClaimBlocks;
    }

    public int accruedIdleThreshold()
    {
        return this.accruedIdleThreshold;
    }

    public int accruedIdlePercent()
    {
        return this.accruedIdlePercent;
    }

    public double abandonReturnRatio()
    {
        return this.abandonReturnRatio;
    }

    public static ClaimBlockSettings upstreamDefaults()
    {
        return new ClaimBlockSettings(
                DEFAULT_INITIAL_BLOCKS,
                DEFAULT_BLOCKS_ACCRUED_PER_HOUR,
                DEFAULT_MAXIMUM_ACCRUED_CLAIM_BLOCKS,
                DEFAULT_ACCRUED_IDLE_THRESHOLD,
                DEFAULT_ACCRUED_IDLE_PERCENT,
                DEFAULT_MAXIMUM_CLAIMS_PER_PLAYER,
                DEFAULT_ABANDON_RETURN_RATIO
        );
    }
}
