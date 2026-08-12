package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimAccessSubject;
import com.griefprevention.claims.ClaimTrustIdentifier;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimTrustEvaluatorTest
{
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void permissionNodeTrustUsesTheProviderAndRetainsTrustHierarchy()
    {
        Map<String, ClaimTrustLevel> entries = new LinkedHashMap<>();
        entries.put("[gp3d.vip]", ClaimTrustLevel.CONTAINER);
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                entries,
                Collections.emptySet(),
                Collections.emptySet()
        );
        FabricPermissionResolver permissions = (playerId, permission) ->
                PLAYER.equals(playerId) && "gp3d.vip".equals(permission);

        assertTrue(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.ACCESS, permissions));
        assertTrue(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.CONTAINER, permissions));
        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.BUILD, permissions));
    }

    @Test
    void permissionNodeManageTrustDoesNotGrantInteractionTrust()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Collections.emptyMap(),
                Collections.singleton("[gp3d.staff]"),
                Collections.emptySet()
        );
        FabricPermissionResolver permissions = (playerId, permission) ->
                "gp3d.staff".equals(permission);

        assertTrue(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.MANAGE, permissions));
        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.BUILD, permissions));
    }

    @Test
    void deniedPermissionNodeCannotGrantTrustOrFallThroughToPublic()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(
                        "[gp3d.vip]", ClaimTrustLevel.BUILD,
                        "public", ClaimTrustLevel.ACCESS
                ),
                Collections.emptySet(),
                Collections.singleton("[gp3d.vip]")
        );
        FabricPermissionResolver permissions = (playerId, permission) ->
                "gp3d.vip".equals(permission);

        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.BUILD, permissions));
        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.ACCESS, permissions));
    }

    @Test
    void levelSpecificPermissionNodeDenyIsResolvedEvenWithoutALocalGrant()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Collections.singletonMap("public", ClaimTrustLevel.ACCESS),
                Collections.emptySet(),
                Collections.singleton("[gp3d.blocked]#access")
        );
        FabricPermissionResolver permissions = (playerId, permission) ->
                "gp3d.blocked".equals(permission);

        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.ACCESS, permissions));
    }

    @Test
    void undefinedOrExplicitlyDeniedProviderPermissionDoesNotGrantTrust()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Collections.singletonMap("[gp3d.vip]", ClaimTrustLevel.ACCESS),
                Collections.emptySet(),
                Collections.emptySet()
        );

        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.ACCESS, (playerId, permission) -> null));
        assertFalse(FabricClaimTrustEvaluator.allows(
                PLAYER, trust, ClaimTrustLevel.ACCESS, (playerId, permission) -> false));
    }

    @Test
    void subjectContainsOnlyGrantedPermissionIdentifiersReferencedByTheClaim()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(
                        "[gp3d.vip]", ClaimTrustLevel.ACCESS,
                        "public", ClaimTrustLevel.ACCESS,
                        UUID.randomUUID().toString(), ClaimTrustLevel.BUILD
                ),
                Collections.singleton("[gp3d.staff]"),
                Collections.singleton("[gp3d.blocked]#access")
        );
        FabricPermissionResolver permissions = (playerId, permission) ->
                "gp3d.vip".equals(permission) || "gp3d.blocked".equals(permission);

        ClaimAccessSubject subject = FabricClaimTrustEvaluator.subject(PLAYER, trust, permissions);

        assertEquals(Set.of("[gp3d.vip]", "[gp3d.blocked]"), subject.identifiers());
    }

    @Test
    void commandTargetsUseTheSameBukkitPermissionIdentifierShape()
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        assertEquals("[gp3d.vip]", ClaimTrustIdentifier.fromPermissionTarget("gp3d.vip"));
        assertEquals("[gp3d.vip]", ClaimTrustIdentifier.fromPermissionTarget("[GP3D.VIP]"));
        assertEquals("gp3d.vip", ClaimTrustIdentifier.permissionNode("[gp3d.vip]#inventory"));
        assertEquals(
                "[gp3d.vip]",
                StringArgumentType.string().parse(new StringReader("\"[gp3d.vip]\""))
        );
        assertEquals(null, ClaimTrustIdentifier.fromPermissionTarget("offlinePlayer"));
        assertEquals(null, ClaimTrustIdentifier.fromPermissionTarget("[broken"));
    }
}
