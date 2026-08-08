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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
