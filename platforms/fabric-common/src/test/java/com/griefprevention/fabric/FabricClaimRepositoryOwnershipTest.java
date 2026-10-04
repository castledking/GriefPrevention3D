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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /transferclaim, /makeadmin and /makebasic change owners; /ignoreclaims is a session toggle. */
class FabricClaimRepositoryOwnershipTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID RECIPIENT = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID BUILDER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @TempDir
    private Path tempDir;

    @Test
    void aNewOwnerTakesTheClaimAndItsTrustStays() throws Exception
    {
        Path dataFolder = dataStore();
        FabricClaimRepository repository = repository(dataFolder);

        ClaimSnapshot given = repository.changeOwner(1L, RECIPIENT, null);

        assertEquals(RECIPIENT, given.ownerId());
        assertTrue(repository.allows(given, RECIPIENT, ClaimTrustLevel.EDIT));
        assertFalse(repository.allows(given, OWNER, ClaimTrustLevel.ACCESS));
        assertTrue(repository.allows(given, BUILDER, ClaimTrustLevel.BUILD));
        assertEquals(RECIPIENT, repository.topLevelClaimsOwnedBy(RECIPIENT).get(0).ownerId());
        assertTrue(repository.topLevelClaimsOwnedBy(OWNER).isEmpty());
    }

    @Test
    void theNewOwnerIsSaved() throws Exception
    {
        Path dataFolder = dataStore();
        repository(dataFolder).changeOwner(1L, RECIPIENT, null);

        FabricClaimRepository reloaded = repository(dataFolder);

        assertEquals(RECIPIENT, reloaded.getClaim(1L).orElseThrow().ownerId());
        assertTrue(reloaded.getClaim(2L).isPresent(), "the subdivision is kept");
    }

    @Test
    void withoutAnOwnerAClaimBecomesAdministrative() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore());

        ClaimSnapshot admin = repository.changeOwner(1L, null, null);

        assertNull(admin.ownerId());
        assertTrue(repository.isAdminClaim(admin));
        assertEquals(1, repository.topLevelAdminClaims().size());
        assertTrue(repository.isAdminClaim(repository.getClaim(2L).orElseThrow()), "its subdivision follows");
    }

    @Test
    void subdivisionsOnlyChangeHandsWithTheirClaim() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore());

        assertThrows(IllegalArgumentException.class, () -> repository.changeOwner(2L, RECIPIENT, null));
        assertNull(repository.changeOwner(42L, RECIPIENT, null));
    }

    @Test
    void ignoringClaimsIsAToggle()
    {
        FabricClaimRepository repository = new FabricClaimRepository(
                this.tempDir.resolve("empty"), NOPLogger.NOP_LOGGER, (playerId, permission) -> false);

        assertTrue(repository.toggleIgnoringClaims(OWNER));
        assertTrue(repository.isIgnoringClaims(OWNER));
        assertFalse(repository.toggleIgnoringClaims(OWNER));
        repository.toggleIgnoringClaims(OWNER);
        repository.stopIgnoringClaims(OWNER);
        assertFalse(repository.isIgnoringClaims(OWNER));
    }

    private FabricClaimRepository repository(Path dataFolder)
    {
        return new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER, (playerId, permission) -> false);
    }

    /** Claim 1 belongs to OWNER, trusts BUILDER and holds subdivision 2. */
    private Path dataStore() throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimData = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimData);
        Files.createDirectories(dataFolder.resolve("PlayerData"));
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), "3", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("1.yml"), """
                Claim ID: '1'
                Lesser Boundary Corner: world;0;-64;0
                Greater Boundary Corner: world;40;320;40
                Owner: %1$s
                Builders:
                - %2$s
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
                    Parent Claim ID: 1
                    inheritNothing: false
                    Is3D: false
                """.formatted(OWNER, BUILDER), StandardCharsets.UTF_8);
        return dataFolder;
    }
}
