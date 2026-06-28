package com.griefprevention.claims;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimTrustSnapshotTest {

    @Test
    void ownerHasEveryTrustLevel() {
        UUID owner = UUID.randomUUID();
        ClaimTrustSnapshot trust = ClaimTrustSnapshot.empty(owner);

        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.EDIT));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.ACCESS));
    }

    @Test
    void managerTrustGrantsLowerLevelsButNotOwnerOnlyEdit() {
        UUID manager = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            Collections.emptyMap(),
            Collections.singletonList(manager.toString()),
            Collections.emptyList()
        );

        assertFalse(trust.hasExplicitPermission(manager, ClaimTrustLevel.EDIT));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(manager, ClaimTrustLevel.ACCESS));
    }

    @Test
    void buildTrustGrantsContainerAndAccessOnly() {
        UUID builder = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            Collections.singletonMap(builder.toString(), ClaimTrustLevel.BUILD),
            Collections.emptyList(),
            Collections.emptyList()
        );

        assertFalse(trust.hasExplicitPermission(builder, ClaimTrustLevel.EDIT));
        assertFalse(trust.hasExplicitPermission(builder, ClaimTrustLevel.MANAGE));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.CONTAINER));
        assertTrue(trust.hasExplicitPermission(builder, ClaimTrustLevel.ACCESS));
    }

    @Test
    void explicitIdentifiersCannotGrantOwnerOnlyEdit() {
        UUID player = UUID.randomUUID();
        Map<String, ClaimTrustLevel> permissions = new HashMap<>();
        permissions.put(player.toString(), ClaimTrustLevel.EDIT);
        permissions.put("public", ClaimTrustLevel.EDIT);
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            permissions,
            Collections.singletonList("[gp3d.staff]"),
            Collections.emptyList()
        );

        assertFalse(trust.hasExplicitPermission(player, ClaimTrustLevel.EDIT));
        assertFalse(trust.hasPublicPermission(ClaimTrustLevel.EDIT));
        assertFalse(
            trust.hasExplicitPermission(player, Collections.singletonList("[gp3d.staff]"), ClaimTrustLevel.EDIT)
        );
    }

    @Test
    void ownerStillHasOwnerOnlyEdit() {
        UUID owner = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            owner,
            Collections.emptyMap(),
            Collections.emptyList(),
            Collections.emptyList()
        );

        assertTrue(trust.hasExplicitPermission(owner, ClaimTrustLevel.EDIT));
    }

    @Test
    void supportsPublicAndPermissionNodeIdentifiers() {
        UUID player = UUID.randomUUID();
        Map<String, ClaimTrustLevel> permissions = new HashMap<>();
        permissions.put("Public", ClaimTrustLevel.ACCESS);
        permissions.put("[gp3d.vip]", ClaimTrustLevel.CONTAINER);
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            permissions,
            Collections.singletonList("[gp3d.staff]"),
            Collections.emptyList()
        );

        assertTrue(trust.hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertFalse(trust.hasPublicPermission(ClaimTrustLevel.CONTAINER));
        assertTrue(
            trust.hasExplicitPermission(player, Collections.singletonList("[GP3D.VIP]"), ClaimTrustLevel.ACCESS)
        );
        assertTrue(
            trust.hasExplicitPermission(player, Collections.singletonList("[gp3d.vip]"), ClaimTrustLevel.CONTAINER)
        );
        assertFalse(
            trust.hasExplicitPermission(player, Collections.singletonList("[gp3d.vip]"), ClaimTrustLevel.BUILD)
        );
        assertTrue(
            trust.hasExplicitPermission(player, Collections.singletonList("[gp3d.staff]"), ClaimTrustLevel.MANAGE)
        );
    }

    @Test
    void evaluatesPlatformNeutralAccessSubject() {
        UUID player = UUID.randomUUID();
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            Collections.singletonMap("[gp3d.vip]", ClaimTrustLevel.CONTAINER),
            Collections.singletonList("[gp3d.staff]"),
            Collections.singletonList("[gp3d.blocked]#access")
        );

        ClaimAccessSubject vip = ClaimAccessSubject.of(player, Collections.singletonList("[GP3D.VIP]"));
        ClaimAccessSubject staff = ClaimAccessSubject.of(player, Collections.singletonList("[gp3d.staff]"));
        ClaimAccessSubject blocked = ClaimAccessSubject.of(player, Collections.singletonList("[gp3d.blocked]"));

        assertTrue(trust.hasExplicitPermission(vip, ClaimTrustLevel.ACCESS));
        assertTrue(trust.hasExplicitPermission(vip, ClaimTrustLevel.CONTAINER));
        assertFalse(trust.hasExplicitPermission(vip, ClaimTrustLevel.BUILD));
        assertTrue(trust.hasExplicitPermission(staff, ClaimTrustLevel.MANAGE));
        assertTrue(trust.isPermissionDenied(blocked, ClaimTrustLevel.ACCESS));
    }

    @Test
    void supportsExactAndLevelSpecificDenyEntries() {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            Arrays.asList("Member", "builder#build", "chest#inventory", "visitor#access")
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
    void exposesImmutableNormalizedCopies() {
        ClaimTrustSnapshot trust = new ClaimTrustSnapshot(
            null,
            Collections.singletonMap("  Player  ", ClaimTrustLevel.ACCESS),
            Collections.singletonList(" Manager "),
            Collections.singletonList(" Denied ")
        );

        assertTrue(trust.permissionsByIdentifier().containsKey("player"));
        assertTrue(trust.managerIdentifiers().contains("manager"));
        assertTrue(trust.deniedIdentifiers().contains("denied"));
        assertThrows(UnsupportedOperationException.class, () ->
            trust.permissionsByIdentifier().put("other", ClaimTrustLevel.ACCESS)
        );
        assertThrows(UnsupportedOperationException.class, () -> trust.managerIdentifiers().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> trust.deniedIdentifiers().add("other"));
    }
}
