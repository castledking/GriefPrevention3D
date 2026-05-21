package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCornerDetectionTest
{
    @Test
    void rectangularCornerDetectionHandlesOneBlockXAndZDimensions()
    {
        assertCornerDetected(new3DClaim(5, 64, 5, 5, 64, 5), 5, 5);
        assertCornerDetected(new3DClaim(5, 64, 5, 5, 65, 6), 5, 5);
        assertCornerDetected(new3DClaim(5, 64, 5, 5, 65, 6), 5, 6);
        assertCornerDetected(new3DClaim(5, 64, 5, 6, 65, 5), 5, 5);
        assertCornerDetected(new3DClaim(5, 64, 5, 6, 65, 5), 6, 5);
    }

    @Test
    void rectangularCornerDetectionRejectsNonBoundaryColumns()
    {
        Claim claim = new3DClaim(5, 64, 5, 7, 65, 7);

        assertEquals(-1, claim.getCornerIndexAt(6, 5));
        assertEquals(-1, claim.getCornerIndexAt(5, 6));
        assertEquals(-1, claim.getCornerIndexAt(6, 6));
    }

    @Test
    void threeDCornerRaycastOnlyAppliesToInitialSelectionClicks()
    {
        PlayerData playerData = new PlayerData();
        playerData.shovelMode = ShovelMode.Subdivide3D;

        assertTrue(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));

        playerData.lastShovelLocation = new Location(null, 5, 64, 5);
        assertFalse(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));

        playerData.lastShovelLocation = null;
        playerData.claimResizing = new3DClaim(5, 64, 5, 5, 64, 5);
        assertFalse(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));

        playerData.claimResizing = null;
        playerData.claimSubdividing = new3DClaim(5, 64, 5, 5, 64, 5);
        assertFalse(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));
    }

    @Test
    void threeDCornerRaycastSkipsNonClaimSelectionModes()
    {
        PlayerData playerData = new PlayerData();

        playerData.shovelMode = ShovelMode.RestoreNature;
        assertFalse(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));

        playerData.shovelMode = ShovelMode.Shaped;
        assertFalse(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));

        playerData.shovelMode = ShovelMode.Admin3D;
        assertTrue(ClaimToolInteractionState.shouldAttempt3DCornerSelection(playerData));
    }

    private static void assertCornerDetected(Claim claim, int x, int z)
    {
        assertTrue(claim.getCornerIndexAt(x, z) >= 0);
    }

    private static Claim new3DClaim(int x1, int y1, int z1, int x2, int y2, int z2)
    {
        return new Claim(
                new Location(null, x1, y1, z1),
                new Location(null, x2, y2, z2),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                1L,
                true);
    }
}
