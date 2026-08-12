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

        String config = Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8);
        String messages = Files.readString(dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8);
        assertTrue(config.contains("GriefPrevention:"));
        assertTrue(config.contains("Claim Blocks Accrued Per Hour:"));
        assertTrue(config.contains("Max Accrued Claim Blocks:"));
        assertTrue(config.contains("Accrued Idle Threshold: 0"));
        assertTrue(config.contains("AccruedIdlePercent: 0"));
        assertTrue(config.contains("MaximumNumberOfClaimsPerPlayer: 0"));
        assertTrue(config.contains("AbandonReturnRatio: 1.0"));
        assertTrue(messages.contains("Messages:"));
        assertTrue(messages.contains("ClaimCreationFailedOverClaimCountLimit:"));
        assertTrue(messages.contains("PlaceholderTrustLevelUntrusted: \"Untrusted\""));
    }

    @Test
    void preservesUnknownTopLevelValuesWhileAddingTheFabricRoots() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("config.yml"), "custom: true\n", StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        String updated = Files.readString(dataFolder.resolve("config.yml"), StandardCharsets.UTF_8);
        assertTrue(updated.startsWith("custom: true\n"));
        assertTrue(updated.contains("GriefPrevention:"));
    }

    @Test
    void addsMissingDefaultsToAnExistingConfigWithoutReplacingCustomValues() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Path configFile = dataFolder.resolve("config.yml");
        String existingConfig = """
                # Keep this administrator comment.
                GriefPrevention:
                  ConfigVersion: 1
                  Claims:
                    Mode:
                      world: Survival
                    MinimumArea: 144
                  AddonSettings:
                    CustomValue: fire
                """;
        Files.writeString(configFile, existingConfig, StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        String updated = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(updated.contains("# Keep this administrator comment."));
        assertTrue(updated.contains("MinimumArea: 144"));
        assertTrue(updated.contains("AddonSettings:\n    CustomValue: fire"));
        assertTrue(updated.contains("InitialBlocks: 100"));
        assertTrue(updated.contains("Claim Blocks Accrued Per Hour:\n      Default: 100"));
        assertTrue(updated.contains("Max Accrued Claim Blocks:\n      Default: 80000"));
        assertTrue(updated.contains("Accrued Idle Threshold: 0"));
        assertTrue(updated.contains("AccruedIdlePercent: 0"));
        assertTrue(updated.contains("MaximumNumberOfClaimsPerPlayer: 0"));
        assertTrue(updated.contains("AbandonReturnRatio: 1.0"));

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        assertEquals(updated, Files.readString(configFile, StandardCharsets.UTF_8));
    }

    @Test
    void migratesLegacyClaimBlockValuesIntoMissingCanonicalDefaults() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Path configFile = dataFolder.resolve("config.yml");
        Files.writeString(configFile, """
                GriefPrevention:
                  Claims:
                    BlocksAccruedPerHour: 625
                    MaxAccruedBlocks: 91234
                    AccruedIdleThreshold: 45
                """, StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        String updated = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(updated.contains("BlocksAccruedPerHour: 625"));
        assertTrue(updated.contains("Claim Blocks Accrued Per Hour:\n      Default: 625"));
        assertTrue(updated.contains("MaxAccruedBlocks: 91234"));
        assertTrue(updated.contains("Max Accrued Claim Blocks:\n      Default: 91234"));
        assertTrue(updated.contains("AccruedIdleThreshold: 45"));
        assertTrue(updated.contains("Accrued Idle Threshold: 45"));
    }

    @Test
    void addsMissingMessageDefaultsWithoutReplacingCustomMessages() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Path messagesFile = dataFolder.resolve("messages.yml");
        Files.writeString(messagesFile, """
                # Keep the custom wording and addon key.
                Messages:
                  BlockNotClaimed: "Custom block message"
                  AddonMessage: "Custom addon message"
                """, StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(dataFolder, NOPLogger.NOP_LOGGER);

        String updated = Files.readString(messagesFile, StandardCharsets.UTF_8);
        assertTrue(updated.contains("# Keep the custom wording and addon key."));
        assertTrue(updated.contains("BlockNotClaimed: \"Custom block message\""));
        assertTrue(updated.contains("AddonMessage: \"Custom addon message\""));
        assertTrue(updated.contains("ClaimCreationFailedOverClaimCountLimit:"));
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
