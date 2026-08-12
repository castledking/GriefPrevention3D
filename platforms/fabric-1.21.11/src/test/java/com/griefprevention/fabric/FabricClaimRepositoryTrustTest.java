package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimRepositoryTrustTest
{
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @TempDir
    private Path tempDir;

    @Test
    void loadedPaperPermissionTrustFlowsThroughTheRepositoryEvaluator() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimData = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimData);
        Files.createDirectories(dataFolder.resolve("PlayerData"));
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), "2", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("1.yml"), """
                Claim ID: '1'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;10;320;10
                Owner: ''
                Builders: []
                Containers:
                - '[gp3d.vip]'
                Accessors: []
                Managers: []
                Parent Claim ID: -1
                Is3D: false
                """, StandardCharsets.UTF_8);
        FabricPermissionResolver permissions = (playerId, permission) ->
                PLAYER.equals(playerId) && "gp3d.vip".equals(permission);

        FabricClaimRepository repository = new FabricClaimRepository(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                permissions
        );
        ClaimSnapshot claim = repository.getClaim(1L).orElseThrow();

        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.ACCESS));
        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.CONTAINER));
        assertFalse(repository.allows(claim, PLAYER, ClaimTrustLevel.BUILD));
    }

    @Test
    void manageTrustDoesNotRevokeLoadedContainerTrust() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimData = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimData);
        Files.createDirectories(dataFolder.resolve("PlayerData"));
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), "2", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("1.yml"), """
                Claim ID: '1'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;10;320;10
                Owner: ''
                Builders: []
                Containers:
                - aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
                Accessors: []
                Managers:
                - aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
                Parent Claim ID: -1
                Is3D: false
                """, StandardCharsets.UTF_8);

        FabricClaimRepository repository = new FabricClaimRepository(
                dataFolder,
                NOPLogger.NOP_LOGGER,
                (playerId, permission) -> false
        );
        ClaimSnapshot claim = repository.getClaim(1L).orElseThrow();

        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.MANAGE));
        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.CONTAINER));
        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.ACCESS));
        assertFalse(repository.allows(claim, PLAYER, ClaimTrustLevel.BUILD));
    }
}
