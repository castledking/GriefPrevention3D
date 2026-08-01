package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricDataFolderTest
{
    @TempDir
    private Path tempDir;

    @Test
    void createsPaperStyleConfigAndMessagesDefaults() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

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

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        assertEquals("custom: true\n", Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void usesExistingPaperDataAsTheSharedCanonicalStore() throws Exception
    {
        Path gameDir = this.tempDir.resolve("server");
        Path configDir = gameDir.resolve("config");
        Path paperData = gameDir.resolve("plugins").resolve("GriefPreventionData");
        writeValidDataStore(paperData, 17L, "\n1200\n300\n\n");

        Path resolved = FabricDataFolder.resolveSharedDataFolder(gameDir, configDir, NOPLogger.NOP_LOGGER);

        assertEquals(paperData, resolved);
        assertEquals("17", Files.readString(paperData.resolve("ClaimData").resolve("_nextClaimID")));
        assertFalse(Files.exists(configDir.resolve("GriefPreventionData")));
    }

    @Test
    void validatesAndAtomicallyImportsThePreviousFabricStoreWithoutRemovingIt() throws Exception
    {
        Path gameDir = this.tempDir.resolve("server");
        Path configDir = gameDir.resolve("config");
        Path oldFabricData = configDir.resolve("GriefPreventionData");
        Path paperData = gameDir.resolve("plugins").resolve("GriefPreventionData");
        String playerBalance = "\n1200\n300\n\n";
        writeValidDataStore(oldFabricData, 17L, playerBalance);

        Path resolved = FabricDataFolder.resolveSharedDataFolder(gameDir, configDir, NOPLogger.NOP_LOGGER);

        assertEquals(paperData, resolved);
        assertTrue(Files.isDirectory(oldFabricData), "the old Fabric store is the rollback backup");
        assertEquals(playerBalance, Files.readString(
                paperData.resolve("PlayerData").resolve(PLAYER_ID.toString()),
                StandardCharsets.UTF_8
        ));
        assertTrue(Files.isRegularFile(paperData.resolve(FabricDataFolder.LOCATION_MIGRATION_MARKER)));
        assertEquals(1, FabricClaimFileStore.load(paperData, NOPLogger.NOP_LOGGER).documents().size());

        assertEquals(
                paperData,
                FabricDataFolder.resolveSharedDataFolder(gameDir, configDir, NOPLogger.NOP_LOGGER),
                "the marker must make subsequent boots deterministic while the backup remains"
        );
    }

    @Test
    void refusesToChooseBetweenUnrelatedPaperAndFabricStores() throws Exception
    {
        Path gameDir = this.tempDir.resolve("server");
        Path configDir = gameDir.resolve("config");
        Path oldFabricData = configDir.resolve("GriefPreventionData");
        Path paperData = gameDir.resolve("plugins").resolve("GriefPreventionData");
        writeValidDataStore(oldFabricData, 17L, "\n10\n20\n\n");
        writeValidDataStore(paperData, 44L, "\n30\n40\n\n");

        assertThrows(
                IllegalStateException.class,
                () -> FabricDataFolder.resolveSharedDataFolder(gameDir, configDir, NOPLogger.NOP_LOGGER)
        );

        assertEquals("17", Files.readString(oldFabricData.resolve("ClaimData").resolve("_nextClaimID")));
        assertEquals("44", Files.readString(paperData.resolve("ClaimData").resolve("_nextClaimID")));
    }

    @Test
    void malformedPreviousFabricStoreIsNeverPromoted() throws Exception
    {
        Path gameDir = this.tempDir.resolve("server");
        Path configDir = gameDir.resolve("config");
        Path oldFabricData = configDir.resolve("GriefPreventionData");
        Path paperData = gameDir.resolve("plugins").resolve("GriefPreventionData");
        Path malformed = oldFabricData.resolve("ClaimData").resolve("1.yml");
        Files.createDirectories(malformed.getParent());
        Files.writeString(oldFabricData.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(malformed, "Claim ID: '1'\nOwner: ''\n", StandardCharsets.UTF_8);

        assertThrows(
                IllegalStateException.class,
                () -> FabricDataFolder.resolveSharedDataFolder(gameDir, configDir, NOPLogger.NOP_LOGGER)
        );

        assertEquals("Claim ID: '1'\nOwner: ''\n", Files.readString(malformed, StandardCharsets.UTF_8));
        assertFalse(Files.exists(paperData));
    }

    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private static void writeValidDataStore(Path dataFolder, long nextClaimId, String playerBalance)
            throws Exception
    {
        Path claimData = dataFolder.resolve("ClaimData");
        Path playerData = dataFolder.resolve("PlayerData");
        Files.createDirectories(claimData);
        Files.createDirectories(playerData);
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), String.valueOf(nextClaimId), StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("1.yml"), """
                Claim ID: '1'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;10;320;10
                Owner: ''
                Builders: []
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: -1
                Is3D: false
                """, StandardCharsets.UTF_8);
        Files.writeString(playerData.resolve(PLAYER_ID.toString()), playerBalance, StandardCharsets.UTF_8);
    }
}
