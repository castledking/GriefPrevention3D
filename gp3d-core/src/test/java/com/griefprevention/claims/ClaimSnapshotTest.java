package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSnapshotTest
{
    @Test
    void rejectsContainmentAndOverlapInDifferentWorlds()
    {
        ClaimSnapshot first = snapshot("world", null, false, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10));
        ClaimSnapshot second = snapshot("world_nether", null, false, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10));

        assertFalse(first.contains("world_nether", 5, 5, 5, false));
        assertFalse(first.overlaps(second));
    }

    @Test
    void respectsTopLevelTwoDimensionalWorldHeight()
    {
        ClaimSnapshot claim = snapshot("world", null, false, false, ClaimBounds.rectangle(0, -64, 0, 10, 320, 10));

        assertTrue(claim.contains("world", 5, -64, 5, false));
        assertTrue(claim.contains("world", 5, 320, 5, false));
        assertFalse(claim.contains("world", 5, -65, 5, false));
        assertTrue(claim.contains("world", 5, -65, 5, true));
    }

    @Test
    void ignoresHeightForTwoDimensionalSubdivisions()
    {
        ClaimSnapshot claim = snapshot("world", 1L, false, true, ClaimBounds.rectangle(0, 10, 0, 10, 20, 10));

        assertTrue(claim.contains("world", 5, -100, 5, false));
        assertTrue(claim.contains("world", 5, 500, 5, false));
    }

    @Test
    void usesHeightForThreeDimensionalClaims()
    {
        ClaimSnapshot claim = snapshot("world", 1L, true, true, ClaimBounds.rectangle(0, 10, 0, 10, 20, 10));

        assertTrue(claim.contains("world", 5, 10, 5, false));
        assertTrue(claim.contains("world", 5, 20, 5, false));
        assertFalse(claim.contains("world", 5, 9, 5, false));
        assertTrue(claim.contains("world", 5, 9, 5, true));
    }

    @Test
    void overlapsMatchClaimHeightRules()
    {
        ClaimSnapshot low3d = snapshot("world", 1L, true, true, ClaimBounds.rectangle(0, 0, 0, 10, 5, 10));
        ClaimSnapshot high3d = snapshot("world", 1L, true, true, ClaimBounds.rectangle(0, 6, 0, 10, 10, 10));
        ClaimSnapshot high2d = snapshot("world", 1L, false, true, ClaimBounds.rectangle(0, 6, 0, 10, 10, 10));
        ClaimSnapshot low2d = snapshot("world", 1L, false, true, ClaimBounds.rectangle(0, 0, 0, 10, 5, 10));

        assertFalse(low3d.overlaps(high3d));
        assertTrue(low2d.overlaps(high2d));
        assertFalse(low3d.overlaps(high2d));
    }

    @Test
    void ownerlessSnapshotIsAdminClaim()
    {
        ClaimSnapshot admin = snapshot("world", null, false, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10));
        ClaimSnapshot player = snapshot("world", null, false, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10), UUID.randomUUID());

        assertTrue(admin.adminClaim());
        assertFalse(player.adminClaim());
    }

    private static ClaimSnapshot snapshot(
            String world,
            Long parentId,
            boolean threeDimensional,
            boolean subdivision,
            ClaimBounds bounds)
    {
        return snapshot(world, parentId, threeDimensional, subdivision, bounds, null);
    }

    private static ClaimSnapshot snapshot(
            String world,
            Long parentId,
            boolean threeDimensional,
            boolean subdivision,
            ClaimBounds bounds,
            UUID ownerId)
    {
        return new ClaimSnapshot(2L, world, ownerId, parentId, bounds, threeDimensional, subdivision);
    }
}
