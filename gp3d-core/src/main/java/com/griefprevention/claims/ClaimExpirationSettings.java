package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

/**
 * The {@code Claims.Expiration} settings of config.yml, with the new-player claim radius and check rate they
 * depend on, using Paper's defaults.
 */
public final class ClaimExpirationSettings
{
    public static final int DEFAULT_CHEST_CLAIM_DAYS = 7;
    public static final int DEFAULT_DAYS_INACTIVE = 60;
    public static final int DEFAULT_EXCEPT_TOTAL_CLAIM_BLOCKS = 10000;
    public static final int DEFAULT_EXCEPT_BONUS_CLAIM_BLOCKS = 5000;
    public static final int DEFAULT_NEW_PLAYER_CLAIM_RADIUS = 4;
    public static final int DEFAULT_CHECK_RATE_SECONDS = 60;

    private final int chestClaimDays;
    private final int daysInactive;
    private final int exceptWhenOwnerHasTotalClaimBlocks;
    private final int exceptWhenOwnerHasBonusClaimBlocks;
    private final int automaticNewPlayerClaimsRadius;
    private final int checkRateSeconds;

    /**
     * @param chestClaimDays {@code Claims.Expiration.ChestClaimDays}; 0 or less turns it off
     * @param daysInactive {@code Claims.Expiration.AllClaims.DaysInactive}; 0 or less turns it off
     * @param automaticNewPlayerClaimsRadius {@code Claims.AutomaticNewPlayerClaimsRadius}, which sizes a
     *        new-player claim; below 0 means there are none
     * @param checkRateSeconds {@code Advanced.ClaimExpirationCheckRate}: seconds between two owners' checks
     */
    public ClaimExpirationSettings(
            int chestClaimDays,
            int daysInactive,
            int exceptWhenOwnerHasTotalClaimBlocks,
            int exceptWhenOwnerHasBonusClaimBlocks,
            int automaticNewPlayerClaimsRadius,
            int checkRateSeconds)
    {
        this.chestClaimDays = chestClaimDays;
        this.daysInactive = daysInactive;
        this.exceptWhenOwnerHasTotalClaimBlocks = exceptWhenOwnerHasTotalClaimBlocks;
        this.exceptWhenOwnerHasBonusClaimBlocks = exceptWhenOwnerHasBonusClaimBlocks;
        this.automaticNewPlayerClaimsRadius = automaticNewPlayerClaimsRadius;
        this.checkRateSeconds = Math.max(1, checkRateSeconds);
    }

    public static @NotNull ClaimExpirationSettings upstreamDefaults()
    {
        return new ClaimExpirationSettings(
                DEFAULT_CHEST_CLAIM_DAYS,
                DEFAULT_DAYS_INACTIVE,
                DEFAULT_EXCEPT_TOTAL_CLAIM_BLOCKS,
                DEFAULT_EXCEPT_BONUS_CLAIM_BLOCKS,
                DEFAULT_NEW_PLAYER_CLAIM_RADIUS,
                DEFAULT_CHECK_RATE_SECONDS);
    }

    public int chestClaimDays()
    {
        return this.chestClaimDays;
    }

    public int daysInactive()
    {
        return this.daysInactive;
    }

    public int exceptWhenOwnerHasTotalClaimBlocks()
    {
        return this.exceptWhenOwnerHasTotalClaimBlocks;
    }

    public int exceptWhenOwnerHasBonusClaimBlocks()
    {
        return this.exceptWhenOwnerHasBonusClaimBlocks;
    }

    public int automaticNewPlayerClaimsRadius()
    {
        return this.automaticNewPlayerClaimsRadius;
    }

    public int checkRateSeconds()
    {
        return this.checkRateSeconds;
    }

    /** Whether any claim can expire at all. */
    public boolean enabled()
    {
        return this.chestClaimDays > 0 || this.daysInactive > 0;
    }

    /** The area of the claim a new player gets, which a lone claim no bigger than counts as. */
    public long newPlayerClaimArea()
    {
        if (this.automaticNewPlayerClaimsRadius < 0)
        {
            return 0L;
        }
        long side = this.automaticNewPlayerClaimsRadius * 2L + 1L;
        return side * side;
    }
}
