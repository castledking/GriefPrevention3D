package com.griefprevention.claims;

/** Reproduces Bukkit's ten-minute claim-block delivery and cap arithmetic. */
public final class ClaimBlockAccrual
{
    private static final int DELIVERIES_PER_HOUR = 6;

    private ClaimBlockAccrual()
    {
    }

    public static int blocksForDelivery(int blocksPerHour, boolean idle, int idlePercent)
    {
        if (idle)
        {
            if (idlePercent <= 0)
            {
                return 0;
            }
            blocksPerHour = (int) (blocksPerHour * (idlePercent / 100.0D));
        }

        int blocks = blocksPerHour / DELIVERIES_PER_HOUR;
        return blocks < 0 ? 0 : blocks;
    }

    public static int addPendingBlocks(int pendingBlocks, int blocksToAccrue)
    {
        // PlayerData uses ordinary int addition for its in-memory holding area.
        return pendingBlocks + blocksToAccrue;
    }

    public static int materializeAccruedBlocks(
            int accruedBlocks,
            int pendingBlocks,
            int maximumAccruedBlocks)
    {
        if (pendingBlocks > 0 && accruedBlocks < maximumAccruedBlocks)
        {
            // Preserve upstream overflow behavior before applying the configured cap.
            return Math.min(accruedBlocks + pendingBlocks, maximumAccruedBlocks);
        }
        return accruedBlocks;
    }

    public static int idleThresholdSquared(int idleThreshold)
    {
        // DeliverClaimBlocksTask performs this multiplication as an int.
        return idleThreshold * idleThreshold;
    }
}
