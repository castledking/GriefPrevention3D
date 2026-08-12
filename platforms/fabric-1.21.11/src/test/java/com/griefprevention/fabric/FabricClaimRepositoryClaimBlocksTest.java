package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.persistence.ClaimDataSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimRepositoryClaimBlocksTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    private Path tempDir;

    @Test
    void createResizeAndAbandonUseDerivedBalanceWithoutMutatingPlayerEntitlements() throws Exception
    {
        Path dataFolder = currentDataStore("\n100\n0\n\n");
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        String originalPlayerData = Files.readString(playerFile, StandardCharsets.UTF_8);
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);

        FabricClaimRepository.CreateClaimResult tooLarge = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 10, 320, 9),
                OWNER,
                null
        );
        assertTrue(tooLarge.hasInsufficientClaimBlocks());
        assertEquals(10, tooLarge.blocksNeeded());
        assertEquals(0, repository.claimCount());

        FabricClaimRepository.CreateClaimResult created = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 9, 320, 9),
                OWNER,
                null
        );
        assertTrue(created.created());
        assertEquals(0, created.remainingBlocks());
        assertNotNull(created.createdClaim());
        long claimId = created.createdClaim().id();

        FabricClaimRepository.UpdateClaimResult unaffordableResize = repository.updateClaimBounds(
                claimId,
                ClaimBounds.rectangle(0, -64, 0, 10, 320, 9),
                null
        );
        assertTrue(unaffordableResize.hasInsufficientClaimBlocks());
        assertEquals(10, unaffordableResize.blocksNeeded());
        assertEquals(100, repository.getClaim(claimId).orElseThrow().bounds().area());

        FabricClaimRepository.UpdateClaimResult shrink = repository.updateClaimBounds(
                claimId,
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                null
        );
        assertTrue(shrink.updated());
        assertEquals(75, shrink.remainingBlocks());

        assertNotNull(repository.deleteClaim(claimId, null));
        assertFalse(repository.getClaim(claimId).isPresent());
        ClaimBlockBalance afterAbandon = repository.claimBlockBalance(OWNER);
        assertEquals(100, afterAbandon.remaining());
        assertEquals(originalPlayerData, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void malformedPlayerRecordPreventsClaimMutationAndIsNeverOverwritten() throws Exception
    {
        String malformed = "\nnot-a-number\n0\n\n";
        Path dataFolder = currentDataStore(malformed);
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);

        assertThrows(IOException.class, () -> repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                OWNER,
                null
        ));

        assertEquals(0, repository.claimCount());
        assertEquals(malformed, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void maximumClaimCountRejectsAnotherTopLevelClaimUnlessExplicitlyBypassed() throws Exception
    {
        Path dataFolder = currentDataStore(
                "\n100\n0\n\n",
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 100\n"
                        + "    MaximumNumberOfClaimsPerPlayer: 2\n"
        );
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);

        assertTrue(repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                OWNER,
                null
        ).created());
        assertTrue(repository.createClaim(
                "world",
                ClaimBounds.rectangle(10, -64, 10, 14, 320, 14),
                OWNER,
                null
        ).created());

        FabricClaimRepository.CreateClaimResult limited = repository.createClaim(
                "world",
                ClaimBounds.rectangle(20, -64, 20, 24, 320, 24),
                OWNER,
                null
        );
        assertTrue(limited.hasReachedClaimCountLimit());
        assertFalse(limited.created());
        assertEquals(2, repository.claimCount());

        FabricClaimRepository.CreateClaimResult bypassed = repository.createClaim(
                "world",
                ClaimBounds.rectangle(20, -64, 20, 24, 320, 24),
                OWNER,
                null,
                true
        );
        assertTrue(bypassed.created());
        assertEquals(3, repository.claimCount());
    }

    @Test
    void partialAbandonReturnUpdatesOnlyAccruedBlocksAndSurvivesReload() throws Exception
    {
        String originalPlayerData =
                "legacy-last-login\r\n100\r\n0\r\nlegacy-claims\r\naddon-line\r\n";
        Path dataFolder = currentDataStore(
                originalPlayerData,
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 100\n"
                        + "    AbandonReturnRatio: 0.5\n"
        );
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);

        FabricClaimRepository.CreateClaimResult created = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                OWNER,
                null
        );
        long claimId = created.createdClaim().id();

        assertNotNull(repository.deleteClaimAs(claimId, OWNER, null));
        assertEquals(
                "legacy-last-login\r\n87\r\n0\r\nlegacy-claims\r\naddon-line\r\n",
                Files.readString(playerFile, StandardCharsets.UTF_8)
        );
        assertEquals(87, repository.claimBlockBalance(OWNER).remaining());

        FabricClaimRepository reloaded = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);
        assertEquals(0, reloaded.claimCount());
        assertEquals(87, reloaded.claimBlockBalance(OWNER).remaining());
    }

    @Test
    void nonOwnerDeletionDoesNotApplyTheOwnersAbandonPenalty() throws Exception
    {
        String originalPlayerData = "\n100\n0\n\n";
        Path dataFolder = currentDataStore(
                originalPlayerData,
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 100\n"
                        + "    AbandonReturnRatio: 0.5\n"
        );
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);
        FabricClaimRepository.CreateClaimResult created = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                OWNER,
                null
        );

        assertNotNull(repository.deleteClaimAs(created.createdClaim().id(), UUID.randomUUID(), null));
        assertEquals(originalPlayerData, Files.readString(playerFile, StandardCharsets.UTF_8));
        assertEquals(100, repository.claimBlockBalance(OWNER).remaining());
    }

    @Test
    void malformedPlayerRecordPreventsPenalizedAbandonBeforeClaimDeletion() throws Exception
    {
        Path dataFolder = currentDataStore(
                "\n100\n0\n\n",
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 100\n"
                        + "    AbandonReturnRatio: 0.5\n"
        );
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);
        FabricClaimRepository.CreateClaimResult created = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 4, 320, 4),
                OWNER,
                null
        );
        long claimId = created.createdClaim().id();
        String malformed = "\nnot-a-number\n0\n\n";
        Files.writeString(playerFile, malformed, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> repository.deleteClaimAs(claimId, OWNER, null));
        assertTrue(repository.getClaim(claimId).isPresent());
        assertEquals(1, repository.claimCount());
        assertEquals(malformed, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void persistedPlaytimeAccrualFundsClaimsAcrossRepositoryReloads() throws Exception
    {
        String originalPlayerData = "legacy-login\r\n100\r\n0\r\nlegacy-claims\r\naddon\r\n";
        Path dataFolder = currentDataStore(originalPlayerData);
        Path playerFile = dataFolder.resolve("PlayerData").resolve(OWNER.toString());
        FabricClaimRepository repository = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);
        repository.claimBlockService().accrueBlocks(OWNER, 16);

        FabricClaimRepository.CreateClaimResult created = repository.createClaim(
                "world",
                ClaimBounds.rectangle(0, -64, 0, 10, 320, 9),
                OWNER,
                null
        );

        assertTrue(created.created());
        assertEquals(6, created.remainingBlocks());
        assertEquals(
                "legacy-login\r\n116\r\n0\r\nlegacy-claims\r\naddon\r\n",
                Files.readString(playerFile, StandardCharsets.UTF_8)
        );

        FabricClaimRepository reloaded = new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER);
        assertEquals(1, reloaded.claimCount());
        assertEquals(6, reloaded.claimBlockBalance(OWNER).remaining());
    }

    private Path currentDataStore(String playerData) throws Exception
    {
        return currentDataStore(
                playerData,
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 100\n"
        );
    }

    private Path currentDataStore(String playerData, String config) throws Exception
    {
        Path dataFolder = this.tempDir.resolve(UUID.randomUUID().toString());
        Path claimDataFolder = dataFolder.resolve("ClaimData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(claimDataFolder);
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                dataFolder.resolve("_schemaVersion"),
                String.valueOf(ClaimDataSchema.CURRENT_VERSION),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dataFolder.resolve("config.yml"),
                config,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                playerDataFolder.resolve(OWNER.toString()),
                playerData,
                StandardCharsets.UTF_8
        );
        return dataFolder;
    }
}
