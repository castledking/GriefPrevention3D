package com.griefprevention.protection;

import com.griefprevention.protection.ExplosionProtectionSettings.ClaimWorldMode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The config.yml rules for environmental grief around claims: fire, fluids, pistons, mob block
 * changes, creature and player protection. Names and defaults match the Paper plugin.
 */
public final class WorldProtectionSettings
{
    private final boolean fireSpreads;
    private final boolean fireDestroys;
    private final boolean fireSpreadsInClaims;
    private final boolean fireDamagesInClaims;
    private final @NotNull PistonMode pistonMode;
    private final boolean endermenMoveBlocks;
    private final boolean creaturesTrampleCrops;
    private final boolean protectCreatures;
    private final boolean noCombatInPlayerClaims;
    private final boolean noCombatInAdminClaims;
    private final boolean noCombatInAdminSubdivisions;
    private final boolean pvpToggleForClaims;
    private final boolean pvpToggleForSubdivisions;
    private final @NotNull Map<String, ClaimWorldMode> worldModes;

    public WorldProtectionSettings(
            boolean fireSpreads,
            boolean fireDestroys,
            boolean fireSpreadsInClaims,
            boolean fireDamagesInClaims,
            @NotNull PistonMode pistonMode,
            boolean endermenMoveBlocks,
            boolean creaturesTrampleCrops,
            boolean protectCreatures,
            boolean noCombatInPlayerClaims,
            boolean noCombatInAdminClaims,
            boolean noCombatInAdminSubdivisions,
            boolean pvpToggleForClaims,
            boolean pvpToggleForSubdivisions,
            @NotNull Map<String, ClaimWorldMode> worldModes)
    {
        this.fireSpreads = fireSpreads;
        this.fireDestroys = fireDestroys;
        this.fireSpreadsInClaims = fireSpreadsInClaims;
        this.fireDamagesInClaims = fireDamagesInClaims;
        this.pistonMode = pistonMode;
        this.endermenMoveBlocks = endermenMoveBlocks;
        this.creaturesTrampleCrops = creaturesTrampleCrops;
        this.protectCreatures = protectCreatures;
        this.noCombatInPlayerClaims = noCombatInPlayerClaims;
        this.noCombatInAdminClaims = noCombatInAdminClaims;
        this.noCombatInAdminSubdivisions = noCombatInAdminSubdivisions;
        this.pvpToggleForClaims = pvpToggleForClaims;
        this.pvpToggleForSubdivisions = pvpToggleForSubdivisions;
        this.worldModes = Collections.unmodifiableMap(new LinkedHashMap<>(worldModes));
    }

    public static @NotNull WorldProtectionSettings upstreamDefaults()
    {
        return new WorldProtectionSettings(
                false,
                false,
                false,
                false,
                PistonMode.CLAIMS_ONLY,
                false,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                Collections.<String, ClaimWorldMode>emptyMap()
        );
    }

    /** {@code GriefPrevention.FireSpreads}: whether fire spreads at all, in claims or not. */
    public boolean fireSpreads()
    {
        return this.fireSpreads;
    }

    /** {@code GriefPrevention.FireDestroys}: whether fire burns blocks away at all. */
    public boolean fireDestroys()
    {
        return this.fireDestroys;
    }

    /** {@code GriefPrevention.Claims.FireSpreadsInClaims} */
    public boolean fireSpreadsInClaims()
    {
        return this.fireSpreadsInClaims;
    }

    /** {@code GriefPrevention.Claims.FireDamagesInClaims} */
    public boolean fireDamagesInClaims()
    {
        return this.fireDamagesInClaims;
    }

    public @NotNull PistonMode pistonMode()
    {
        return this.pistonMode;
    }

    public boolean endermenMoveBlocks()
    {
        return this.endermenMoveBlocks;
    }

    public boolean creaturesTrampleCrops()
    {
        return this.creaturesTrampleCrops;
    }

    /** {@code GriefPrevention.Claims.ProtectCreatures}: whether claimed animals and pets are protected. */
    public boolean protectCreatures()
    {
        return this.protectCreatures;
    }

    /** {@code GriefPrevention.PvP.ProtectPlayersInLandClaims.PlayerOwnedClaims} */
    public boolean noCombatInPlayerClaims()
    {
        return this.noCombatInPlayerClaims;
    }

    /** {@code GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeClaims} */
    public boolean noCombatInAdminClaims()
    {
        return this.noCombatInAdminClaims;
    }

    /** {@code GriefPrevention.PvP.ProtectPlayersInLandClaims.AdministrativeSubdivisions} */
    public boolean noCombatInAdminSubdivisions()
    {
        return this.noCombatInAdminSubdivisions;
    }

    /** {@code GriefPrevention.Claims.PvPToggle.Claim.Enabled}: whether /claimpvp governs top-level claims. */
    public boolean pvpToggleForClaims()
    {
        return this.pvpToggleForClaims;
    }

    /** {@code GriefPrevention.Claims.PvPToggle.Subdivision.Enabled} */
    public boolean pvpToggleForSubdivisions()
    {
        return this.pvpToggleForSubdivisions;
    }

    /**
     * Whether these rules apply in a world. Paper leaves worlds with claims disabled alone, and
     * unlisted worlds allow claims.
     */
    public boolean appliesTo(@NotNull String worldKey)
    {
        return this.worldModes.get(worldKey) != ClaimWorldMode.DISABLED;
    }
}
