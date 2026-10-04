package com.griefprevention.protection;

import com.griefprevention.claims.ClaimTrustLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The config.yml switches behind Paper's rules for right-clicking blocks in a claim. */
public final class BlockUseSettings
{
    private final boolean preventTheft;
    private final boolean lockWoodenDoors;
    private final boolean lockTrapDoors;
    private final boolean lockFenceGates;
    private final boolean preventButtonsSwitches;
    private final boolean lecternReadingRequiresAccessTrust;

    public BlockUseSettings(
            boolean preventTheft,
            boolean lockWoodenDoors,
            boolean lockTrapDoors,
            boolean lockFenceGates,
            boolean preventButtonsSwitches,
            boolean lecternReadingRequiresAccessTrust)
    {
        this.preventTheft = preventTheft;
        this.lockWoodenDoors = lockWoodenDoors;
        this.lockTrapDoors = lockTrapDoors;
        this.lockFenceGates = lockFenceGates;
        this.preventButtonsSwitches = preventButtonsSwitches;
        this.lecternReadingRequiresAccessTrust = lecternReadingRequiresAccessTrust;
    }

    /** Paper's defaults: doors and trapdoors open freely, everything else is locked. */
    public static @NotNull BlockUseSettings upstreamDefaults()
    {
        return new BlockUseSettings(true, false, false, true, true, true);
    }

    /**
     * The trust a claim asks of a player who uses a block, as Paper's interaction rules decide it.
     *
     * @return the trust needed, or null when anyone may use the block
     */
    public @Nullable ClaimTrustLevel requiredTrust(@NotNull BlockUseKind kind)
    {
        switch (kind)
        {
            case CONTAINER:
                return this.preventTheft ? ClaimTrustLevel.CONTAINER : null;
            case DOOR:
                return this.lockWoodenDoors ? ClaimTrustLevel.ACCESS : null;
            case TRAPDOOR:
                return this.lockTrapDoors ? ClaimTrustLevel.ACCESS : null;
            case FENCE_GATE:
                return this.lockFenceGates ? ClaimTrustLevel.ACCESS : null;
            case BED:
            case SWITCH:
                return this.preventButtonsSwitches ? ClaimTrustLevel.ACCESS : null;
            case LECTERN:
                return this.lecternReadingRequiresAccessTrust ? ClaimTrustLevel.ACCESS : null;
            case LECTERN_BOOK:
                return ClaimTrustLevel.CONTAINER;
            case BUILD:
                return ClaimTrustLevel.BUILD;
            default:
                return null;
        }
    }

    public boolean preventTheft()
    {
        return this.preventTheft;
    }

    public boolean lockWoodenDoors()
    {
        return this.lockWoodenDoors;
    }

    public boolean lockTrapDoors()
    {
        return this.lockTrapDoors;
    }

    public boolean lockFenceGates()
    {
        return this.lockFenceGates;
    }

    public boolean preventButtonsSwitches()
    {
        return this.preventButtonsSwitches;
    }

    public boolean lecternReadingRequiresAccessTrust()
    {
        return this.lecternReadingRequiresAccessTrust;
    }
}
