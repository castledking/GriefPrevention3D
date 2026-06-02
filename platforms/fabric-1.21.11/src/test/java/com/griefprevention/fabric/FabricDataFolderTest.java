package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricDataFolderTest
{
    @TempDir
    private Path tempDir;

    @Test
    void createsPaperStyleConfigAndMessagesDefaults() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");

        FabricDataFolder.ensureDefaults(dataFolder, LoggerFactory.getLogger(FabricDataFolderTest.class));

        assertTrue(Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8)
                .contains("GriefPrevention:"));
        assertTrue(Files.readString(dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8)
                .contains("Messages:"));
        assertTrue(Files.readString(dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8)
                .contains("PlaceholderTrustLevelUntrusted: \"Untrusted\""));
    }

    @Test
    void doesNotOverwriteExistingFiles() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("config.yml"), "custom: true\n", StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(dataFolder, LoggerFactory.getLogger(FabricDataFolderTest.class));

        assertEquals("custom: true\n", Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8));
    }
}
