package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2D and 3D subdivisions: who may use them, where they may go, and restricting them. */
class FabricClaimRepositorySubdivisionTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BUILDER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID STRANGER = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final int FLOOR = 40;
    private static final int TOP = 319;

    @TempDir
    private Path tempDir;

    @Test
    void theClaimOwnerMayDoAnythingInTheirSubdivisions() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));
        ClaimSnapshot subdivision = repository.getClaim(2L).orElseThrow();

        assertTrue(repository.allows(subdivision, OWNER, ClaimTrustLevel.BUILD));
        assertTrue(repository.allows(subdivision, OWNER, ClaimTrustLevel.EDIT));
        assertFalse(repository.allows(subdivision, STRANGER, ClaimTrustLevel.ACCESS));
    }

    @Test
    void aSubdivisionHonoursItsParentsTrust() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        assertTrue(repository.allows(repository.getClaim(2L).orElseThrow(), BUILDER, ClaimTrustLevel.BUILD));
    }

    @Test
    void aRestrictedSubdivisionDoesNot() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore("inheritNothing: true"));

        assertFalse(repository.allows(repository.getClaim(2L).orElseThrow(), BUILDER, ClaimTrustLevel.BUILD));
        assertTrue(repository.allows(repository.getClaim(2L).orElseThrow(), OWNER, ClaimTrustLevel.BUILD));
    }

    @Test
    void beingDeniedInASubdivisionBlocksTheParentsTrust() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore("Denied:\n  - " + BUILDER + "#build"));

        assertFalse(repository.allows(repository.getClaim(2L).orElseThrow(), BUILDER, ClaimTrustLevel.BUILD));
    }

    @Test
    void onlyStaffReshapeAnAdministrativeSubdivision() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore("Admin Subdivision: true"));
        ClaimSnapshot staffSpace = repository.getClaim(2L).orElseThrow();

        assertTrue(repository.isAdminSubdivision(staffSpace));
        assertFalse(repository.allows(staffSpace, OWNER, ClaimTrustLevel.EDIT));
        assertFalse(repository.allows(staffSpace, OWNER, ClaimTrustLevel.MANAGE));
        assertTrue(repository.allows(staffSpace, BUILDER, ClaimTrustLevel.BUILD));
    }

    @Test
    void aTwoDimensionalSubdivisionIsSavedInsideItsParentForFree() throws Exception
    {
        Path dataFolder = dataStore("");
        FabricClaimRepository repository = repository(dataFolder);
        int remainingBefore = repository.claimBlockBalance(OWNER).remaining();

        FabricClaimRepository.SubdivisionResult result =
                repository.createSubdivision(1L, ClaimBounds.rectangle(20, FLOOR, 20, 30, TOP, 30), false, null);

        ClaimSnapshot created = result.createdClaim();
        assertNotNull(created);
        assertEquals(1L, created.parentId());
        assertFalse(created.threeDimensional());
        assertEquals(remainingBefore, repository.claimBlockBalance(OWNER).remaining());
        ClaimSnapshot reloaded = repository(dataFolder).getClaim(created.id()).orElseThrow();
        assertEquals(created.bounds(), reloaded.bounds());
        assertEquals(1L, reloaded.parentId());
        assertTrue(repository(dataFolder).allows(reloaded, BUILDER, ClaimTrustLevel.BUILD));
    }

    @Test
    void threeDimensionalSubdivisionsStackButMayNotOverlap() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        ClaimSnapshot ground = repository.createSubdivision(1L,
                ClaimBounds.rectangle(20, 60, 20, 30, 70, 30), true, null).createdClaim();
        ClaimSnapshot upstairs = repository.createSubdivision(1L,
                ClaimBounds.rectangle(20, 71, 20, 30, 80, 30), true, null).createdClaim();
        FabricClaimRepository.SubdivisionResult clash = repository.createSubdivision(1L,
                ClaimBounds.rectangle(25, 65, 25, 35, 75, 35), true, null);

        assertNotNull(ground);
        assertNotNull(upstairs);
        assertTrue(upstairs.threeDimensional());
        assertNull(clash.createdClaim());
        assertEquals(FabricClaimRepository.PlacementProblem.OVERLAPS_SIBLING, clash.problem());
    }

    @Test
    void aSubdivisionMustStayInItsParentAndOffItsSiblings() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        FabricClaimRepository.SubdivisionResult outside = repository.createSubdivision(1L,
                ClaimBounds.rectangle(35, FLOOR, 35, 45, TOP, 45), false, null);
        FabricClaimRepository.SubdivisionResult onSibling = repository.createSubdivision(1L,
                ClaimBounds.rectangle(3, FLOOR, 3, 8, TOP, 8), false, null);

        assertEquals(FabricClaimRepository.PlacementProblem.OUTSIDE_PARENT, outside.problem());
        assertEquals(FabricClaimRepository.PlacementProblem.OVERLAPS_SIBLING, onSibling.problem());
        assertEquals(2L, onSibling.conflictingClaim().id());
    }

    @Test
    void aClaimWithSubdivisionsCanGrowButNotShrinkPastThem() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        FabricClaimRepository.UpdateClaimResult grown = repository.updateClaimBounds(1L,
                ClaimBounds.rectangle(0, FLOOR, 0, 45, TOP, 45), null);
        FabricClaimRepository.UpdateClaimResult shrunk = repository.updateClaimBounds(1L,
                ClaimBounds.rectangle(4, FLOOR, 4, 45, TOP, 45), null);

        assertNotNull(grown.updatedClaim(), "a claim does not collide with its own subdivisions");
        assertEquals(FabricClaimRepository.PlacementProblem.EXCLUDES_SUBDIVISION, shrunk.placementProblem());
    }

    @Test
    void aResizedSubdivisionStaysInItsParent() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        FabricClaimRepository.UpdateClaimResult tooFar = repository.updateClaimBounds(2L,
                ClaimBounds.rectangle(1, FLOOR, 1, 45, TOP, 5), null);
        FabricClaimRepository.UpdateClaimResult larger = repository.updateClaimBounds(2L,
                ClaimBounds.rectangle(1, FLOOR, 1, 15, TOP, 15), null);

        assertEquals(FabricClaimRepository.PlacementProblem.OUTSIDE_PARENT, tooFar.placementProblem());
        assertNotNull(larger.updatedClaim());
    }

    @Test
    void restrictingASubdivisionDropsTheGrantsItInheritedAsCopies() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore("Builders:\n  - " + BUILDER));

        assertEquals(Boolean.TRUE, repository.toggleSubdivisionRestriction(2L));
        ClaimSnapshot subdivision = repository.getClaim(2L).orElseThrow();
        assertTrue(repository.isRestrictedSubdivision(subdivision));
        assertFalse(repository.allows(subdivision, BUILDER, ClaimTrustLevel.BUILD));

        assertEquals(Boolean.FALSE, repository.toggleSubdivisionRestriction(2L));
        assertTrue(repository.allows(repository.getClaim(2L).orElseThrow(), BUILDER, ClaimTrustLevel.BUILD));
    }

    @Test
    void restrictingAClaimRestrictsItsSubdivisionsNowAndLater() throws Exception
    {
        FabricClaimRepository repository = repository(dataStore(""));

        assertEquals(Boolean.TRUE, repository.toggleNewSubdivisionRestriction(1L));
        ClaimSnapshot later = repository.createSubdivision(1L,
                ClaimBounds.rectangle(20, FLOOR, 20, 30, TOP, 30), false, null).createdClaim();

        assertTrue(repository.isRestrictedSubdivision(repository.getClaim(2L).orElseThrow()));
        assertTrue(repository.isRestrictedSubdivision(later));
        assertEquals(Boolean.FALSE, repository.toggleNewSubdivisionRestriction(1L));
        assertFalse(repository.isRestrictedSubdivision(repository.getClaim(2L).orElseThrow()));
    }

    private FabricClaimRepository repository(Path dataFolder)
    {
        return new FabricClaimRepository(dataFolder, NOPLogger.NOP_LOGGER, (playerId, permission) -> false);
    }

    /**
     * Claim 1 (x/z 0..40) belongs to OWNER and trusts BUILDER to build; subdivision 2 (x/z 1..10)
     * carries {@code subdivisionFields}, one YAML line each, in place of its defaults.
     */
    private Path dataStore(String subdivisionFields) throws Exception
    {
        Path dataFolder = this.tempDir.resolve("GriefPreventionData");
        Path claimData = dataFolder.resolve("ClaimData");
        Files.createDirectories(claimData);
        Files.createDirectories(dataFolder.resolve("PlayerData"));
        Files.writeString(dataFolder.resolve("_schemaVersion"), "11", StandardCharsets.UTF_8);
        Files.writeString(claimData.resolve("_nextClaimID"), "3", StandardCharsets.UTF_8);
        // Enough accrued blocks for the owner to grow the claim.
        Files.writeString(dataFolder.resolve("PlayerData").resolve(OWNER.toString()), "\n10000\n0\n\n",
                StandardCharsets.UTF_8);

        List<String> subdivision = new ArrayList<>(List.of(
                "Claim ID: '2'",
                "Lesser Boundary Corner: world;1;" + FLOOR + ";1",
                "Greater Boundary Corner: world;10;" + TOP + ";10",
                "Owner: ''",
                "Containers: []",
                "Accessors: []",
                "Managers: []",
                "Parent Claim ID: 1",
                "Is3D: false"));
        if (!subdivisionFields.contains("Builders:"))
        {
            subdivision.add("Builders: []");
        }
        if (!subdivisionFields.contains("inheritNothing:"))
        {
            subdivision.add("inheritNothing: false");
        }
        if (!subdivisionFields.isEmpty())
        {
            subdivision.addAll(List.of(subdivisionFields.split("\n")));
        }

        StringBuilder yaml = new StringBuilder("""
                Claim ID: '1'
                Lesser Boundary Corner: world;0;%3$d;0
                Greater Boundary Corner: world;40;%4$d;40
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
                """.formatted(OWNER, BUILDER, FLOOR, TOP));
        for (String line : subdivision)
        {
            yaml.append("    ").append(line).append('\n');
        }
        Files.writeString(claimData.resolve("1.yml"), yaml.toString(), StandardCharsets.UTF_8);
        return dataFolder;
    }
}
