package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.protection.BlockPoint;
import com.griefprevention.protection.ExplosionProtectionSettings.ClaimWorldMode;
import com.griefprevention.protection.WorldProtectionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricWeavingProtectionTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID VISITOR = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final BlockPoint INSIDE = new BlockPoint(5, 64, 5);
    private static final BlockPoint OUTSIDE = new BlockPoint(-1, 64, 5);

    @TempDir Path directory;

    @Test
    void untrustedPlayersCannotPlaceWebs() throws Exception
    {
        Fixture f = fixture();
        assertFalse(f.protection.mayPlaceWeb("world", INSIDE, f.visitor(), false));
    }

    @Test
    void mobsCannotPlaceWebsInClaims() throws Exception
    {
        Fixture f = fixture();
        assertFalse(f.protection.mayPlaceWeb("world", INSIDE, null, false));
    }

    @Test
    void containerTrustDoesNotGrantWebPlacement() throws Exception
    {
        Fixture f = fixture();
        f.grant(ClaimTrustLevel.CONTAINER);
        assertFalse(f.protection.mayPlaceWeb("world", INSIDE, f.visitor(), false));
    }

    @Test
    void buildersAndOwnersMayPlaceWebs() throws Exception
    {
        Fixture f = fixture();
        f.grant(ClaimTrustLevel.BUILD);
        assertTrue(f.protection.mayPlaceWeb("world", INSIDE, f.visitor(), false));
        assertTrue(f.protection.mayPlaceWeb("world", INSIDE, f.owner(), false));
    }

    @Test
    void wildernessAndDisabledWorldsStayUnrestricted() throws Exception
    {
        Fixture f = fixture();
        assertTrue(f.protection.mayPlaceWeb("world", OUTSIDE, f.visitor(), false));
        assertTrue(f.protection.mayPlaceWeb("world", OUTSIDE, null, false));
        assertTrue(f.protection.mayApplyToCreature("world", OUTSIDE, null, null));
        FabricWeavingProtection disabled = new FabricWeavingProtection(f.repository, settings(ClaimWorldMode.DISABLED));
        assertTrue(disabled.mayPlaceWeb("world", INSIDE, f.visitor(), false));
        assertTrue(disabled.mayPlaceWeb("world", INSIDE, null, false));
        assertTrue(disabled.mayApplyToCreature("world", INSIDE, null, null));
    }

    @Test
    void creativeWildernessRequiresBuildPermissionUnlessIgnoringClaims() throws Exception
    {
        Fixture f = fixture();
        FabricWeavingProtection creative = new FabricWeavingProtection(f.repository, settings(ClaimWorldMode.CREATIVE));
        assertFalse(creative.mayPlaceWeb("world", OUTSIDE, f.visitor(), false));
        assertTrue(creative.mayPlaceWeb("world", OUTSIDE, f.visitor(), true));
    }

    @Test
    void restricted3DSubdivisionChecksEachWebsActualHeight() throws Exception
    {
        Fixture f = fixture();
        f.grant(ClaimTrustLevel.BUILD);
        ClaimSnapshot child = f.repository.createSubdivision(1L,
                ClaimBounds.rectangle(2, 60, 2, 9, 70, 9), true, null).createdClaim();
        f.repository.toggleSubdivisionRestriction(child.id());
        assertFalse(f.protection.mayPlaceWeb("world", new BlockPoint(5, 70, 5), f.visitor(), false));
        assertTrue(f.protection.mayPlaceWeb("world", new BlockPoint(5, 71, 5), f.visitor(), false));
    }

    @Test
    void inheritingSubdivisionAllowsItsParentsBuilders() throws Exception
    {
        Fixture f = fixture();
        f.grant(ClaimTrustLevel.BUILD);
        f.repository.createSubdivision(1L, ClaimBounds.rectangle(2, 60, 2, 9, 70, 9), true, null);
        assertTrue(f.protection.mayPlaceWeb("world", INSIDE, f.visitor(), false));
    }

    @Test
    void permissionBypassIsUsedForClaimedWebs() throws Exception
    {
        Fixture f = fixture();
        assertTrue(f.protection.mayPlaceWeb("world", INSIDE, (claim, trust) -> true, true));
    }

    @Test
    void weavingOnClaimedCreaturesRequiresContainerTrust() throws Exception
    {
        Fixture f = fixture();
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, f.visitor(), null));
        f.grant(ClaimTrustLevel.ACCESS);
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, f.visitor(), null));
        f.grant(ClaimTrustLevel.CONTAINER);
        assertTrue(f.protection.mayApplyToCreature("world", INSIDE, f.visitor(), null));
        assertTrue(f.protection.mayApplyToCreature("world", INSIDE, f.owner(), null));
    }

    @Test
    void dispensersMayOnlyApplyWeavingInTheirExactClaim() throws Exception
    {
        Fixture f = fixture();
        assertTrue(f.protection.mayApplyToCreature("world", INSIDE, null, origin("world", INSIDE)));
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, null, origin("world", OUTSIDE)));
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, null,
                origin("world", new BlockPoint(25, 64, 5))));
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, null, origin("another_world", INSIDE)));
    }

    @Test
    void missingPotionSourceCannotAffectClaimedCreatures() throws Exception
    {
        Fixture f = fixture();
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, null, null));
    }

    @Test
    void dispenserCannotCrossA3DSubdivisionBoundary() throws Exception
    {
        Fixture f = fixture();
        f.repository.createSubdivision(1L, ClaimBounds.rectangle(2, 60, 2, 9, 70, 9), true, null);
        assertFalse(f.protection.mayApplyToCreature("world", INSIDE, null,
                origin("world", new BlockPoint(5, 71, 5))));
        assertTrue(f.protection.mayApplyToCreature("world", INSIDE, null, origin("world", INSIDE)));
    }

    private static FabricPotionSources.Origin origin(String world, BlockPoint point)
    {
        return new FabricPotionSources.Origin(world, point);
    }

    private Fixture fixture() throws Exception
    {
        Path folder = directory.resolve("GriefPreventionData");
        Path data = folder.resolve("ClaimData");
        Files.createDirectories(data);
        Files.createDirectories(folder.resolve("PlayerData"));
        Files.writeString(folder.resolve("_schemaVersion"), "11");
        Files.writeString(data.resolve("_nextClaimID"), "3");
        for (int id = 1; id <= 2; id++)
        {
            int min = (id - 1) * 20;
            Files.writeString(data.resolve(id + ".yml"), """
                    Claim ID: '%d'
                    Lesser Boundary Corner: world;%d;-64;0
                    Greater Boundary Corner: world;%d;319;19
                    Owner: '%s'
                    Builders: []
                    Containers: []
                    Accessors: []
                    Managers: []
                    Parent Claim ID: -1
                    Is3D: false
                    """.formatted(id, min, min + 19, OWNER));
        }
        return new Fixture(new FabricClaimRepository(folder, NOPLogger.NOP_LOGGER, (player, permission) -> false));
    }

    private static WorldProtectionSettings settings(ClaimWorldMode mode)
    {
        WorldProtectionSettings d = WorldProtectionSettings.upstreamDefaults();
        return new WorldProtectionSettings(d.fireSpreads(), d.fireDestroys(), d.fireSpreadsInClaims(),
                d.fireDamagesInClaims(), d.pistonMode(), d.endermenMoveBlocks(), d.creaturesTrampleCrops(),
                d.protectCreatures(), d.noCombatInPlayerClaims(), d.noCombatInAdminClaims(),
                d.noCombatInAdminSubdivisions(), d.pvpToggleForClaims(), d.pvpToggleForSubdivisions(),
                Map.of("world", mode));
    }

    private static final class Fixture
    {
        final FabricClaimRepository repository;
        final FabricWeavingProtection protection;

        Fixture(FabricClaimRepository repository)
        {
            this.repository = repository;
            this.protection = new FabricWeavingProtection(repository, WorldProtectionSettings.upstreamDefaults());
        }

        BiPredicate<ClaimSnapshot, ClaimTrustLevel> visitor()
        {
            return (claim, trust) -> repository.allows(claim, VISITOR, trust);
        }

        BiPredicate<ClaimSnapshot, ClaimTrustLevel> owner()
        {
            return (claim, trust) -> repository.allows(claim, OWNER, trust);
        }

        void grant(ClaimTrustLevel trust) throws Exception
        {
            repository.grantTrust(List.of(repository.getClaim(1L).orElseThrow()), VISITOR.toString(), trust);
        }
    }
}
