package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTrustSnapshotTest
{
    @Test
    void ownerHasEveryTrustLevel()
    {
        UUID owner = UUID.randomUUID();
        ClaimTrustSnapshot trust = ClaimTrustSnapshot.empty(owner);

        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.EDIT));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.ACCESS));
    }

    @Test
    void managerTrustGrantsLowerLevelsButNotOwnerOnlyEdit()
    {
        UUID manager = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(),
                List.of(manager.toString()),
                List.of()
        );

        assertFalse(trust.hasExplicitPermission(manager, ClaimTrustLevel.EDIT));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.ACCESS));
    }

    @Test
    void buildTrustGrantsContainerAndAccessOnly()
    {
        UUID builder = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(builder.toString(), ClaimTrustLevel.BUILD),
                List.of(),
                List.of()
        );

        assertFalse(trust.hasExplicitPermission(builder, ClaimTrustLevel.EDIT));
        assertFalse(trust.hasExplicitPermission(builder, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.ACCESS));
    }

    @Test
    void explicitIdentifiersCannotGrantOwnerOnlyEdit()
    {
        UUID player = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(
                        player.toString(), ClaimTrustLevel.EDIT,
                        "public", ClaimTrustLevel.EDIT
                ),
                List.of("[gp3d.staff]"),
                List.of()
        );

        assertFalse(trust.hasExplicitPermission(player, ClaimTrustLevel.EDIT));
        assertFalse(trust.hasPublicPermission(ClaimTrustLevel.EDIT));
        assertFalse(trust.hasExplicitPermission(player, List.of("[gp3d.staff]"), ClaimTrustLevel.EDIT));
    }

    @Test
    void ownerStillHasOwnerOnlyEdit()
    {
        UUID owner = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                owner,
                Map.of(),
                List.of(),
                List.of()
        );

        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.EDIT));
    }

    @Test
    void supportsPublicAndPermissionNodeIdentifiers()
    {
        UUID player = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(
                        "Public", ClaimTrustLevel.ACCESS,
                        "[gp3d.vip]", ClaimTrustLevel.CONTAINER
                ),
                List.of("[gp3d.staff]"),
                List.of()
        );

        assertTrue(trust.hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertFalse(trust.hasPublicPermission(ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(player, List.of("[GP3D.VIP]"), ClaimTrustLevel.ACCESS));
        assertTrue(trust.hasExplicitPermission(player, List.of("[gp3d.vip]"), ClaimTrustLevel.CONTAINER));
        assertFalse(trust.hasExplicitPermission(player, List.of("[gp3d.vip]"), ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(player, List.of("[gp3d.staff]"), ClaimTrustLevel.MANAGE));
    }

    @Test
    void evaluatesPlatformNeutralAccessSubject()
    {
        UUID player = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of("[gp3d.vip]", ClaimTrustLevel.CONTAINER),
                List.of("[gp3d.staff]"),
                List.of("[gp3d.blocked]#access")
        );

        ClaimAccessSubject vip = ClaimAccessSubject.of(player, List.of("[GP3D.VIP]"));
        ClaimAccessSubject staff = ClaimAccessSubject.of(player, List.of("[gp3d.staff]"));
        ClaimAccessSubject blocked = ClaimAccessSubject.of(player, List.of("[gp3d.blocked]"));

        assertTrue(trust.hasExplicitPermission(vip, ClaimTrustLevel.ACCESS));
        assertTrue(trust.hasExplicitPermission(vip, ClaimTrustLevel.CONTAINER));
        assertFalse(trust.hasExplicitPermission(vip, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(staff, ClaimTrustLevel.MANAGE));
        assertTrue(trust.isPermissionDenied(blocked, ClaimTrustLevel.ACCESS));
    }

    @Test
    void supportsExactAndLevelSpecificDenyEntries()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of(),
                List.of(),
                List.of("Member", "builder#build", "chest#inventory", "visitor#access")
        );

        assertTrue(trust.isPermissionDenied("member"));
        assertTrue(trust.isPermissionDenied("MEMBER", ClaimTrustLevel.ACCESS));
        assertTrue(trust.isPermissionDenied("builder", ClaimTrustLevel.BUILD));
        assertFalse(trust.isPermissionDenied("builder", ClaimTrustLevel.CONTAINER));
        assertTrue(trust.isPermissionDenied("chest", ClaimTrustLevel.CONTAINER));
        assertFalse(trust.isPermissionDenied("chest", ClaimTrustLevel.BUILD));
        assertTrue(trust.isPermissionDenied("visitor", ClaimTrustLevel.ACCESS));
    }

    @Test
    void exposesImmutableNormalizedCopies()
    {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
                null,
                Map.of("  Player  ", ClaimTrustLevel.ACCESS),
                List.of(" Manager "),
                List.of(" Denied ")
        );

        assertTrue(trust.permissionsByIdentifier().containsKey("player"));
        assertTrue(trust.managerIdentifiers().contains("manager"));
        assertTrue(trust.deniedIdentifiers().contains("denied"));
        assertThrows(UnsupportedOperationException.class,
                () -> trust.permissionsByIdentifier().put("other", ClaimTrustLevel.ACCESS));
        assertThrows(UnsupportedOperationException.class, () -> trust.managerIdentifiers().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> trust.deniedIdentifiers().add("other"));
    }
}
