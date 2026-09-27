package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimRepositoryTrustTest
{
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");

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
    void loadedManageTrustRetainsContainerGrantAndGrantsBuild() throws Exception
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
        assertTrue(repository.allows(claim, PLAYER, ClaimTrustLevel.BUILD));
    }

    @Test
    void grantingTrustReachesOnlyTheSubdivisionsThatInherit() throws Exception
    {
        Path dataFolder = subdividedDataStore();
        FabricClaimRepository repository = repository(dataFolder);

        repository.grantTrust(List.of(repository.getClaim(1L).orElseThrow()), PLAYER.toString(), ClaimTrustLevel.BUILD);

        FabricClaimRepository reloaded = repository(dataFolder);
        assertEquals(ClaimTrustLevel.BUILD, trustOf(reloaded, 1L));
        assertEquals(ClaimTrustLevel.BUILD, trustOf(reloaded, 2L));
        assertNull(trustOf(reloaded, 4L), "administrative subdivisions are staff-trusted only");
        assertEquals(ClaimTrustLevel.CONTAINER, trustOf(reloaded, 3L), "restricted subdivisions keep their own trust");
        assertTrue(reloaded.trustFor(reloaded.getClaim(2L).orElseThrow()).deniedIdentifiers()
                .contains(PLAYER + "#access"));
    }

    @Test
    void revokingTrustDropsItFromEverySubdivisionButKeepsDenials() throws Exception
    {
        Path dataFolder = subdividedDataStore();
        FabricClaimRepository repository = repository(dataFolder);
        repository.grantTrust(List.of(repository.getClaim(1L).orElseThrow()), PLAYER.toString(), ClaimTrustLevel.MANAGE);

        repository.revokeTrust(List.of(repository.getClaim(1L).orElseThrow()), PLAYER.toString());

        FabricClaimRepository reloaded = repository(dataFolder);
        for (long claimId = 1L; claimId <= 4L; claimId++)
        {
            ClaimTrustSnapshot trust = reloaded.trustFor(reloaded.getClaim(claimId).orElseThrow());
            assertFalse(trust.permissionsByIdentifier().containsKey(PLAYER.toString()), "claim " + claimId);
            assertFalse(trust.managerIdentifiers().contains(PLAYER.toString()), "claim " + claimId);
        }
        assertTrue(reloaded.trustFor(reloaded.getClaim(2L).orElseThrow()).deniedIdentifiers()
                .contains(PLAYER + "#access"));
    }

    @Test
    void clearingTrustEmptiesTheClaimAndItsSubdivisions() throws Exception
    {
        Path dataFolder = subdividedDataStore();
        FabricClaimRepository repository = repository(dataFolder);

        repository.clearTrust(List.of(repository.getClaim(1L).orElseThrow()));

        FabricClaimRepository reloaded = repository(dataFolder);
        for (long claimId = 1L; claimId <= 4L; claimId++)
        {
            assertEquals(ClaimTrustSnapshot.empty(OWNER),
                    withOwner(reloaded.trustFor(reloaded.getClaim(claimId).orElseThrow()), OWNER),
                    "claim " + claimId);
        }
        assertEquals(ClaimTrustLevel.BUILD, trustOf(reloaded, 5L), "other claims are untouched");
    }

    @Test
    void allClaimCommandsActOnTheOwnersTopLevelClaims() throws Exception
    {
        FabricClaimRepository repository = repository(subdividedDataStore());

        assertEquals(List.of(1L, 5L), repository.topLevelClaimsOwnedBy(OWNER).stream()
                .map(ClaimSnapshot::id)
                .sorted()
                .toList());
        assertTrue(repository.hasSubdivisions(repository.getClaim(1L).orElseThrow()));
        assertFalse(repository.hasSubdivisions(repository.getClaim(5L).orElseThrow()));
    }

    private static ClaimTrustLevel trustOf(FabricClaimRepository repository, long claimId)
    {
        return repository.trustFor(repository.getClaim(claimId).orElseThrow())
                .permissionsByIdentifier()
                .get(PLAYER.toString());
    }

    private static ClaimTrustSnapshot withOwner(ClaimTrustSnapshot trust, UUID ownerId)
    {
        return new ClaimTrustSnapshot(ownerId, trust.permissionsByIdentifier(), trust.managerIdentifiers(),
                trust.neighborIdentifiers(), trust.deniedIdentifiers(), trust.pvpTrustedIdentifiers(),
                trust.pveTrustedIdentifiers());
    }

    private static FabricClaimRepository repository(Path dataFolder)
    {
        return new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER, (playerId, permission) -> null);
    }

    /**
     * Claim 1 has an inheriting subdivision (2) that denies the player access, a restricted one (3)
     * where the player has container trust, and a staff-administered one (4). Claim 5 is another of
     * the owner's claims; claim 6 belongs to someone else.
     */
    private Path subdividedDataStore() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimData = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimData);
        Files.createDirectories(dataFolder.resolve("PlayerData"));
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), "7", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("1.yml"), """
                Claim ID: '1'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;40;320;40
                Owner: %1$s
                Builders: []
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: -1
                Is3D: false
                Children:
                  '2':
                    Claim ID: '2'
                    Lesser Boundary Corner: world;1;-64;1
                    Greater Boundary Corner: world;5;320;5
                    Owner: ''
                    Builders: []
                    Containers: []
                    Accessors: []
                    Managers: []
                    Denied:
                    - %2$s#access
                    Parent Claim ID: 1
                    inheritNothing: false
                    Is3D: false
                  '3':
                    Claim ID: '3'
                    Lesser Boundary Corner: world;10;-64;10
                    Greater Boundary Corner: world;15;320;15
                    Owner: ''
                    Builders: []
                    Containers:
                    - %2$s
                    Accessors: []
                    Managers: []
                    Parent Claim ID: 1
                    inheritNothing: true
                    Is3D: false
                  '4':
                    Claim ID: '4'
                    Lesser Boundary Corner: world;20;-64;20
                    Greater Boundary Corner: world;25;320;25
                    Owner: ''
                    Builders: []
                    Containers: []
                    Accessors: []
                    Managers: []
                    Parent Claim ID: 1
                    inheritNothing: false
                    Is3D: false
                    Admin Subdivision: true
                """.formatted(OWNER, PLAYER), StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("5.yml"), """
                Claim ID: '5'
                Lesser Boundary Corner: world;100;-64;100
                Greater Boundary Corner: world;110;320;110
                Owner: %1$s
                Builders:
                - %2$s
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: -1
                Is3D: false
                """.formatted(OWNER, PLAYER), StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("6.yml"), """
                Claim ID: '6'
                Lesser Boundary Corner: world;200;-64;200
                Greater Boundary Corner: world;210;320;210
                Owner: %1$s
                Builders: []
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: -1
                Is3D: false
                """.formatted(OTHER), StandardCharsets.UTF_8);
        return dataFolder;
    }
}
