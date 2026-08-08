package com.griefprevention.claims;

/** The shared claim-block configuration currently consumed by native adapters. */
public final class ClaimBlockSettings
{
    public static final int DEFAULT_INITIAL_BLOCKS = 100;

    private final int initialBlocks;

    public ClaimBlockSettings(int initialBlocks)
    {
        this.initialBlocks = initialBlocks;
    }

    public int initialBlocks()
    {
        return this.initialBlocks;
    }

    public static ClaimBlockSettings upstreamDefaults()
    {
        return new ClaimBlockSettings(DEFAULT_INITIAL_BLOCKS);
    }
}
