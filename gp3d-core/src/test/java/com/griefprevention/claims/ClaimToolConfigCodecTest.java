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
}
