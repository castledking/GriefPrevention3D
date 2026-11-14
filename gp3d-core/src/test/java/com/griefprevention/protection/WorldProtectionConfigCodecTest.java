package com.griefprevention.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProtectionConfigCodecTest
{
    private final WorldProtectionConfigCodec codec = new WorldProtectionConfigCodec();

    @Test
    void protectionSwitchesHonorNestedKeysAndRejectInvalidTypes() throws Exception {
        String input = "GriefPrevention:\n  ProtectItemsDroppedOnDeath:\n    PvPWorlds: true\n    NonPvPWorlds: false\n  PvP:\n    ProtectFreshSpawns: false\n  Claims:\n    PreventCushionMount: true\n";
        assertTrue(codec.decodeSwitch(input, "ProtectItemsDroppedOnDeath", "PvPWorlds", false));
        assertFalse(codec.decodeSwitch(input, "ProtectItemsDroppedOnDeath", "NonPvPWorlds", true));
        assertFalse(codec.decodeSwitch(input, "PvP", "ProtectFreshSpawns", true));
        assertTrue(codec.decodeSwitch(input, "Claims", "PreventCushionMount", false));
        assertTrue(codec.decodeSwitch("", "PvP", "ProtectFreshSpawns", true));
        assertThrows(WorldProtectionConfigException.class, () -> codec.decodeSwitch(
                "GriefPrevention:\n  Claims:\n    PreventCushionMount: sometimes\n", "Claims", "PreventCushionMount", false));
    }

    @Test
    void readsPaperKeys() throws Exception
    {
        WorldProtectionSettings settings = this.codec.decode(
                "GriefPrevention:\n"
                        + "  FireSpreads: true\n"
                        + "  FireDestroys: true\n"
                        + "  PistonMovement: EVERYWHERE\n"
                        + "  EndermenMoveBlocks: true\n"
                        + "  CreaturesTrampleCrops: true\n"
                        + "  Claims:\n"
                        + "    FireSpreadsInClaims: true\n"
                        + "    FireDamagesInClaims: true\n"
                        + "    ProtectCreatures: false\n"
                        + "    PvPToggle:\n"
                        + "      Claim:\n"
                        + "        Enabled: true\n"
                        + "    Mode:\n"
                        + "      world_nether: Disabled\n"
                        + "  PvP:\n"
                        + "    ProtectPlayersInLandClaims:\n"
                        + "      PlayerOwnedClaims: false\n"
        );

        assertTrue(settings.fireSpreads());
        assertTrue(settings.fireDestroys());
        assertTrue(settings.fireSpreadsInClaims());
        assertTrue(settings.fireDamagesInClaims());
        assertEquals(PistonMode.EVERYWHERE, settings.pistonMode());
        assertTrue(settings.endermenMoveBlocks());
        assertTrue(settings.creaturesTrampleCrops());
        assertFalse(settings.protectCreatures());
        assertTrue(settings.pvpToggleForClaims());
        assertFalse(settings.pvpToggleForSubdivisions());
        assertFalse(settings.noCombatInPlayerClaims());
        assertTrue(settings.noCombatInAdminClaims());
        assertFalse(settings.appliesTo("world_nether"));
        assertTrue(settings.appliesTo("world"));
    }

    @Test
    void defaultsMatchPaper() throws Exception
    {
        WorldProtectionSettings settings = this.codec.decode("GriefPrevention: {}\n");

        assertFalse(settings.fireSpreads());
        assertFalse(settings.fireDestroys());
        assertEquals(PistonMode.CLAIMS_ONLY, settings.pistonMode());
        assertFalse(settings.endermenMoveBlocks());
        assertTrue(settings.protectCreatures());
        assertTrue(settings.noCombatInPlayerClaims());
    }

    @Test
    void honoursLegacyPistonFlagsAndUnknownModes() throws Exception
    {
        assertEquals(PistonMode.IGNORED, this.codec.decode(
                "GriefPrevention:\n  CheckPistonMovement: false\n").pistonMode());
        assertEquals(PistonMode.EVERYWHERE_SIMPLE, this.codec.decode(
                "GriefPrevention:\n  LimitPistonsToLandClaims: false\n").pistonMode());
        assertEquals(PistonMode.CLAIMS_ONLY, this.codec.decode(
                "GriefPrevention:\n  PistonMovement: SIDEWAYS\n").pistonMode());
    }

    @Test
    void readsVehiclesRequireBuildTrust() throws Exception
    {
        assertFalse(this.codec.decodeVehiclesRequireBuildTrust("GriefPrevention: {}\n"));
        assertFalse(this.codec.decodeVehiclesRequireBuildTrust("GriefPrevention:\n  Claims:\n    VehiclesRequireBuildTrust: false\n"));
        assertTrue(this.codec.decodeVehiclesRequireBuildTrust("GriefPrevention:\n  Claims:\n    VehiclesRequireBuildTrust: true\n"));
    }

    @Test
    void rejectsValuesOfTheWrongType()
    {
        assertThrows(WorldProtectionConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  FireSpreads: sometimes\n"));
        assertThrows(WorldProtectionConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    Mode:\n      world: Chaos\n"));
    }
}
