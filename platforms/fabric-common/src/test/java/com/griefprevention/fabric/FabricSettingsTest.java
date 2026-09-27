package com.griefprevention.fabric;

import com.griefprevention.protection.PistonMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricSettingsTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricSettingsTest.class);

    @TempDir
    Path dataFolder;

    @Test
    void theSeededConfigCarriesPapersDefaults()
    {
        FabricDataFolder.ensureDefaults(this.dataFolder, LOGGER);

        FabricSettings settings = new FabricSettings(this.dataFolder, LOGGER);

        assertEquals("STICK", settings.tools().investigationTool());
        assertEquals("GOLDEN_SHOVEL", settings.tools().modificationTool());
        assertEquals(5, settings.tools().minimumWidth());
        assertEquals(100, settings.tools().minimumArea());
        assertFalse(settings.tools().visualizationGlow());
        assertFalse(settings.tools().claimsEnabled("world_nether"));
        assertFalse(settings.world().fireSpreads());
        assertFalse(settings.world().fireDestroys());
        assertEquals(PistonMode.CLAIMS_ONLY, settings.world().pistonMode());
        assertTrue(settings.world().protectCreatures());
        assertTrue(settings.world().noCombatInPlayerClaims());
        assertFalse(settings.world().pvpToggleForClaims());
        assertFalse(settings.world().appliesTo("world_the_end"));
    }

    @Test
    void reloadPicksUpEditsAndABadValueFallsBackToTheDefaults() throws Exception
    {
        Path config = this.dataFolder.resolve("config.yml");
        Files.writeString(config, "GriefPrevention:\n  VisualizationGlow: true\n  FireSpreads: true\n",
                StandardCharsets.UTF_8);
        FabricSettings settings = new FabricSettings(this.dataFolder, LOGGER);
        assertTrue(settings.tools().visualizationGlow());
        assertTrue(settings.world().fireSpreads());

        Files.writeString(config, "GriefPrevention:\n  VisualizationGlow: true\n  FireSpreads: maybe\n",
                StandardCharsets.UTF_8);
        settings.reload();

        // One unreadable protection value must not switch every protection off.
        assertFalse(settings.world().fireSpreads());
        assertTrue(settings.tools().visualizationGlow());
    }
}
