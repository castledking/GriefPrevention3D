package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimToolConfigCodecTest
{
    private final ClaimToolConfigCodec codec = new ClaimToolConfigCodec();

    @Test
    void readsToolsSizesGlowAndWorldModes() throws Exception
    {
        ClaimToolSettings settings = this.codec.decode(
                "GriefPrevention:\n"
                        + "  VisualizationGlow: true\n"
                        + "  Claims:\n"
                        + "    InvestigationTool: BLAZE_ROD\n"
                        + "    ModificationTool: mymod:claim_wand\n"
                        + "    MinimumWidth: 8\n"
                        + "    MinimumArea: 64\n"
                        + "    Mode:\n"
                        + "      world: Survival\n"
                        + "      world_nether: Disabled\n"
        );

        assertEquals("BLAZE_ROD", settings.investigationTool());
        assertEquals("mymod:claim_wand", settings.modificationTool());
        assertEquals(8, settings.minimumWidth());
        assertEquals(64, settings.minimumArea());
        assertTrue(settings.visualizationGlow());
        assertTrue(settings.claimsEnabled("world"));
        assertFalse(settings.claimsEnabled("world_nether"));
        assertTrue(settings.claimsEnabled("unlisted"));
    }

    @Test
    void usesUpstreamDefaultsWhenFieldsAreAbsent() throws Exception
    {
        ClaimToolSettings settings = this.codec.decode("GriefPrevention:\n  Claims: {}\n");

        assertEquals("STICK", settings.investigationTool());
        assertEquals("GOLDEN_SHOVEL", settings.modificationTool());
        assertEquals(5, settings.minimumWidth());
        assertEquals(100, settings.minimumArea());
        assertFalse(settings.visualizationGlow());
    }

    @Test
    void translatesBukkitMaterialNamesToItemIds()
    {
        assertEquals("minecraft:stick", ClaimToolSettings.itemId("STICK"));
        assertEquals("minecraft:golden_shovel", ClaimToolSettings.itemId("GOLDEN_SHOVEL"));
        assertEquals("minecraft:golden_shovel", ClaimToolSettings.itemId("GOLD_SPADE"));
        assertEquals("mymod:claim_wand", ClaimToolSettings.itemId("MyMod:Claim_Wand"));
    }

    @Test
    void rejectsValuesOfTheWrongType()
    {
        assertThrows(ClaimToolConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    MinimumWidth: wide\n"));
        assertThrows(ClaimToolConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  VisualizationGlow: sometimes\n"));
        assertThrows(ClaimToolConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    Mode:\n      world: Sometimes\n"));
    }

    @Test
    void transferringClaimsIsOffAndFreeByDefault() throws Exception
    {
        ClaimTransferSettings settings = this.codec.decodeTransfer("GriefPrevention: {}\n");

        assertFalse(settings.enabled());
        assertEquals(0.0, settings.price());
    }

    @Test
    void readsPapersTransferClaimSection() throws Exception
    {
        ClaimTransferSettings settings = this.codec.decodeTransfer(
                "GriefPrevention:\n  Claims:\n    TransferClaim:\n      Enabled: true\n      Price: 250\n");

        assertTrue(settings.enabled());
        assertEquals(250.0, settings.price());
    }

    @Test
    void rejectsANegativeTransferPrice()
    {
        assertThrows(ClaimToolConfigException.class, () -> this.codec
                .decodeTransfer("GriefPrevention:\n  Claims:\n    TransferClaim:\n      Price: -5\n"));
    }

    @Test
    void trappedStaysOutOfAdminClaimsUnlessAllowed() throws Exception
    {
        assertFalse(this.codec.decodeAllowTrappedInAdminClaims("GriefPrevention: {}\n"));
        assertTrue(this.codec.decodeAllowTrappedInAdminClaims(
                "GriefPrevention:\n  Claims:\n    AllowTrappedInAdminClaims: true\n"));
    }

    @Test
    void readsClaimExpirationWithPapersDefaults() throws Exception
    {
        ClaimExpirationSettings defaults = this.codec.decodeClaimExpiration("GriefPrevention: {}\n");
        assertEquals(7, defaults.chestClaimDays());
        assertEquals(60, defaults.daysInactive());
        assertEquals(10000, defaults.exceptWhenOwnerHasTotalClaimBlocks());
        assertEquals(5000, defaults.exceptWhenOwnerHasBonusClaimBlocks());
        assertEquals(81L, defaults.newPlayerClaimArea());
        assertEquals(60, defaults.checkRateSeconds());

        ClaimExpirationSettings configured = this.codec.decodeClaimExpiration(
                "GriefPrevention:\n"
                        + "  Advanced:\n"
                        + "    ClaimExpirationCheckRate: 5\n"
                        + "  Claims:\n"
                        + "    AutomaticNewPlayerClaimsRadius: -1\n"
                        + "    Expiration:\n"
                        + "      ChestClaimDays: 0\n"
                        + "      AllClaims:\n"
                        + "        DaysInactive: 30\n"
                        + "        ExceptWhenOwnerHasTotalClaimBlocks: 2000\n"
                        + "        ExceptWhenOwnerHasBonusClaimBlocks: 100\n");
        assertEquals(0, configured.chestClaimDays());
        assertEquals(30, configured.daysInactive());
        assertEquals(2000, configured.exceptWhenOwnerHasTotalClaimBlocks());
        assertEquals(100, configured.exceptWhenOwnerHasBonusClaimBlocks());
        assertEquals(0L, configured.newPlayerClaimArea());
        assertEquals(5, configured.checkRateSeconds());
        assertTrue(configured.enabled());
    }

    @Test
    void rejectsANonNumericExpirationSetting()
    {
        assertThrows(ClaimToolConfigException.class, () -> this.codec.decodeClaimExpiration(
                "GriefPrevention:\n  Claims:\n    Expiration:\n      AllClaims:\n        DaysInactive: soon\n"));
    }
}
