package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class FabricSettingsProtectionTest {
    @TempDir Path directory;

    @Test void protectionSwitchesReloadWithoutLosingPaperDefaults() throws Exception {
        var settings = new FabricSettings(directory, NOPLogger.NOP_LOGGER);
        assertTrue(settings.protectFreshSpawns());
        assertFalse(settings.preventCushionMount());
        assertFalse(settings.hoppersRequireBuildTrust());
        assertFalse(settings.lockDeathDrops(true));
        assertTrue(settings.lockDeathDrops(false));
        Files.writeString(directory.resolve("config.yml"), """
                GriefPrevention:
                  Claims:
                    PreventCushionMount: true
                    HoppersRequireBuildTrust: true
                  PvP:
                    ProtectFreshSpawns: false
                  ProtectItemsDroppedOnDeath:
                    PvPWorlds: true
                    NonPvPWorlds: false
                """);
        settings.reload();
        assertFalse(settings.protectFreshSpawns());
        assertTrue(settings.preventCushionMount());
        assertTrue(settings.hoppersRequireBuildTrust());
        assertTrue(settings.lockDeathDrops(true));
        assertFalse(settings.lockDeathDrops(false));
    }
}
