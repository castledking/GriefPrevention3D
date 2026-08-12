package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimBlockServiceTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @TempDir
    private Path tempDir;

    @Test
    void readsPaperBalancesAndLuckPermsStyleGroupBonusesWithoutWritingThePlayerFile() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 100\n",
                StandardCharsets.UTF_8
        );
        String originalPlayerData = "legacy-last-login\r\n120\r\n30\r\nlegacy-claims\r\naddon-line\r\n";
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.writeString(playerFile, originalPlayerData, StandardCharsets.UTF_8);
        Files.writeString(playerDataFolder.resolve("$gp3d.vip"), "50\n", StandardCharsets.UTF_8);

        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> OWNER.equals(playerId) && "gp3d.vip".equals(permission)
        );
        service.reload();

        ClaimBounds shaped = lShape();
        List<ClaimSnapshot> claims = Arrays.asList(
                claim(1L, OWNER, null, shaped, false),
                claim(2L, OWNER, 1L, ClaimBounds.rectangle(0, 70, 0, 3, 90, 3), true),
                claim(3L, OTHER, null, ClaimBounds.rectangle(20, -64, 20, 29, 320, 29), false),
                claim(4L, null, null, ClaimBounds.rectangle(40, -64, 40, 49, 320, 49), false)
        );

        ClaimBlockBalance balance = service.balance(OWNER, claims);

        assertEquals(200, balance.totalEntitlement());
        assertEquals(16, balance.claimedArea());
        assertEquals(184, balance.remaining());
        assertEquals(originalPlayerData, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void missingPlayerFileUsesConfiguredInitialBlocksWithoutCreatingARecord() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 450\n",
                StandardCharsets.UTF_8
        );
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );
        service.reload();

        ClaimBlockBalance balance = service.balance(OWNER, List.of());

        assertEquals(450, balance.remaining());
        assertEquals(false, Files.exists(dataFolder.resolve("PlayerData").resolve(OWNER.toString())));
    }

    @Test
    void malformedPlayerDataFailsClosedAndRemainsUntouched() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        String malformed = "\nnot-a-number\n30\n\n";
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.writeString(playerFile, malformed, StandardCharsets.UTF_8);

        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );
        service.reload();

        assertThrows(IOException.class, () -> service.balance(OWNER, List.of()));
        assertEquals(malformed, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void abandonmentUpdateRefusesToOverwriteAConcurrentlyChangedPlayerRecord() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n  Claims:\n    AbandonReturnRatio: 0.5\n",
                StandardCharsets.UTF_8
        );
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.writeString(playerFile, "\n100\n0\n\n", StandardCharsets.UTF_8);
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );
        service.reload();

        FabricClaimBlockService.PlayerDataUpdate update = service.prepareAbandonment(OWNER, 25);
        assertNotNull(update);
        assertEquals(13, update.accruedPenalty());
        assertEquals(87, update.updatedAccruedClaimBlocks());

        String changed = "\n150\n0\n\n";
        Files.writeString(playerFile, changed, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> service.apply(update));
        assertEquals(changed, Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void abandonmentCreatesACompatibleRecordForAPlayerUsingInitialBlocks() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 100\n"
                        + "    AbandonReturnRatio: 0.5\n",
                StandardCharsets.UTF_8
        );
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );
        service.reload();

        FabricClaimBlockService.PlayerDataUpdate update = service.prepareAbandonment(OWNER, 25);
        assertNotNull(update);
        service.apply(update);

        assertEquals(
                "\n87\n0\n\n",
                Files.readString(dataFolder.resolve("PlayerData").resolve(OWNER.toString()))
        );
    }

    @Test
    void deliveredAccrualSurvivesImmediateServiceReplacementWithoutLifecycleFlush() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                playerDataFolder.resolve(OWNER.toString()),
                "\n100\n0\n\n",
                StandardCharsets.UTF_8
        );
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        service.reload();

        service.accrueBlocks(OWNER, 400);

        FabricClaimBlockService restarted = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        restarted.reload();
        assertEquals(500, restarted.balance(OWNER, List.of()).remaining());
    }

    @Test
    void deliveredAccrualUsesTheBukkitCapAndPersistsImmediately() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    Max Accrued Claim Blocks:\n"
                        + "      Default: 125\n",
                StandardCharsets.UTF_8
        );
        String original = "legacy-login\r\n120\r\n30\r\nlegacy-claims\r\naddon-line\r\n";
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.writeString(playerFile, original, StandardCharsets.UTF_8);
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        service.reload();

        service.accrueBlocks(OWNER, 16);
        assertEquals(155, service.balance(OWNER, List.of()).remaining());
        assertEquals(
                "legacy-login\r\n125\r\n30\r\nlegacy-claims\r\naddon-line\r\n",
                Files.readString(playerFile, StandardCharsets.UTF_8)
        );
        assertFalse(service.playersWithAccrualState().contains(OWNER));

        FabricClaimBlockService reloaded = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        reloaded.reload();
        assertEquals(155, reloaded.balance(OWNER, List.of()).remaining());
    }

    @Test
    void deliveredAccrualImmediatelyCreatesTheSharedRecordForANewPlayer() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Files.createDirectories(dataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 450\n",
                StandardCharsets.UTF_8
        );
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        service.reload();

        service.accrueBlocks(OWNER, 16);

        assertEquals(
                "\n466\n0\n\n",
                Files.readString(dataFolder.resolve("PlayerData").resolve(OWNER.toString()))
        );
    }

    @Test
    void failedWriteKeepsAccrualPendingForLifecycleRetry() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.createDirectory(playerFile);
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        service.reload();
        assertThrows(IOException.class, () -> service.accrueBlocks(OWNER, 16));
        assertTrue(service.playersWithAccrualState().contains(OWNER));

        Files.delete(playerFile);
        Files.writeString(playerFile, "\n100\n0\n\n", StandardCharsets.UTF_8);
        service.flushAccrual(OWNER);

        assertEquals("\n116\n0\n\n", Files.readString(playerFile, StandardCharsets.UTF_8));
        assertFalse(service.playersWithAccrualState().contains(OWNER));
    }

    @Test
    void abandonPenaltyConsumesMaterializedSessionAccrualExactlyOnce() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path playerDataFolder = dataFolder.resolve("PlayerData");
        Files.createDirectories(playerDataFolder);
        Files.writeString(
                dataFolder.resolve("config.yml"),
                "GriefPrevention:\n  Claims:\n    AbandonReturnRatio: 0.5\n",
                StandardCharsets.UTF_8
        );
        Path playerFile = playerDataFolder.resolve(OWNER.toString());
        Files.writeString(playerFile, "\n100\n0\n\n", StandardCharsets.UTF_8);
        FabricClaimBlockService service = new FabricClaimBlockService(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        service.reload();
        service.accrueBlocks(OWNER, 16);

        FabricClaimBlockService.PlayerDataUpdate update = service.prepareAbandonment(OWNER, 25);
        assertNotNull(update);
        assertEquals(103, update.updatedAccruedClaimBlocks());
        service.apply(update);
        service.flushAccrual(OWNER);

        assertEquals("\n103\n0\n\n", Files.readString(playerFile, StandardCharsets.UTF_8));
    }

    @Test
    void permissionDefaultsRemainDistinctFromExplicitDenials()
    {
        FabricClaimBlockService undefined = new FabricClaimBlockService(
                this.tempDir,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        FabricClaimBlockService denied = new FabricClaimBlockService(
                this.tempDir,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );

        assertTrue(undefined.permissionOrDefault(OWNER, "griefprevention.accruals", true));
        assertFalse(undefined.permissionOrDefault(OWNER, "custom.permission", false));
        assertFalse(denied.permissionOrDefault(OWNER, "griefprevention.accruals", true));
    }

    private static ClaimBounds lShape()
    {
        return ClaimBounds.shaped(
                OrthogonalPolygon.fromClosedPath(Arrays.asList(
                        new OrthogonalPoint2i(0, 0),
                        new OrthogonalPoint2i(4, 0),
                        new OrthogonalPoint2i(4, 1),
                        new OrthogonalPoint2i(1, 1),
                        new OrthogonalPoint2i(1, 4),
                        new OrthogonalPoint2i(0, 4),
                        new OrthogonalPoint2i(0, 0)
                )),
                -64,
                320
        );
    }

    private static ClaimSnapshot claim(
            Long id,
            UUID owner,
            Long parentId,
            ClaimBounds bounds,
            boolean subdivision)
    {
        return new ClaimSnapshot(id, "world", owner, parentId, bounds, false, subdivision);
    }
}
