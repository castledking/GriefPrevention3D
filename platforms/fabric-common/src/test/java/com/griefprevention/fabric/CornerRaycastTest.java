package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CornerRaycastTest
{
    private static final String WORLD = "minecraft:overworld";
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y = 319;
    private static final double TOOL_REACH = 100.0D;

    @Test
    void farAwayClickOnAnAirCornerSelectsTheCorner()
    {
        Vec3 eye = new Vec3(6.0D, 62.5D, 6.0D);
        Vec3 view = lookFrom(eye, 10.5D, 60.5D, 10.5D);

        CornerRaycast.CornerHit corner = closestOf(subdivision(ClaimBounds.rectangle(10, 60, 10, 14, 64, 14)), eye, view);

        assertNotNull(corner);
        assertEquals(new BlockPos(10, 60, 10), corner.pos());
        // A click on nothing always loses to the corner, which may be the very air aimed at.
        assertTrue(CornerRaycast.beatsClickedBlock(eye, view, null, corner.distanceAlongView()));
    }

    @Test
    void onlyABlockInFrontOfTheCornerKeepsTheClick()
    {
        Vec3 eye = new Vec3(6.0D, 62.5D, 6.0D);
        Vec3 view = lookFrom(eye, 10.5D, 60.5D, 10.5D);

        CornerRaycast.CornerHit corner = closestOf(subdivision(ClaimBounds.rectangle(10, 60, 10, 14, 64, 14)), eye, view);
        assertNotNull(corner);

        // (12, 60, 12) sits inside the subdivision, further along the same view line.
        assertTrue(CornerRaycast.beatsClickedBlock(eye, view, new BlockPos(12, 60, 12), corner.distanceAlongView()));
        // (8, 61, 8) lies well in front of the corner, so the block the player clicked stands.
        assertFalse(CornerRaycast.beatsClickedBlock(eye, view, new BlockPos(8, 61, 8), corner.distanceAlongView()));
        // A block behind the player cannot be the one they clicked at the corner either.
        assertTrue(CornerRaycast.beatsClickedBlock(eye, view, new BlockPos(-20, 60, -20), corner.distanceAlongView()));
    }

    @Test
    void foreignWorldsAreNeverSnapped()
    {
        Vec3 eye = new Vec3(6.0D, 62.5D, 6.0D);
        Vec3 view = lookFrom(eye, 10.5D, 60.5D, 10.5D);

        ClaimSnapshot elsewhere = new ClaimSnapshot(
                1L, "minecraft:the_nether", null, null, ClaimBounds.rectangle(10, 60, 10, 14, 64, 14), true, true);
        assertNull(closestOf(elsewhere, eye, view));
    }

    @Test
    void aCornerUnderWaterIsTheBlockTheViewLinePasses()
    {
        // A parent claim reaches from the world floor to its ceiling, so its corner is a column of
        // water out in the ocean: the click has to find the corner, not the seabed behind it.
        Vec3 eye = new Vec3(10.5D, 65.5D, 0.5D);
        Vec3 view = new Vec3(0.0D, 0.0D, 1.0D);

        CornerRaycast.CornerHit corner = closestOf(subdivision(ClaimBounds.rectangle(10, 60, 10, 14, 70, 14), false), eye, view);

        assertNotNull(corner);
        assertEquals(new BlockPos(10, 65, 10), corner.pos());
    }

    @Test
    void aBlockInsideTheClaimStillLeadsToItsCorner()
    {
        Vec3 eye = new Vec3(12.5D, 65.5D, 0.5D);
        Vec3 view = new Vec3(0.0D, 0.0D, 1.0D);

        // Two blocks off the corner column is within reach of the block one step inside the claim.
        CornerRaycast.CornerHit corner = closestOf(subdivision(ClaimBounds.rectangle(10, 60, 10, 14, 70, 14), false), eye, view);

        assertNotNull(corner);
        assertEquals(new BlockPos(10, 65, 10), corner.pos());
    }

    @Test
    void theCornerOfAClaimWithoutAHeightKeepsToThatHeight()
    {
        Vec3 view = new Vec3(0.0D, 0.0D, 1.0D);
        ClaimBounds bounds = ClaimBounds.rectangle(10, 60, 10, 14, 70, 14);
        ClaimSnapshot claim = subdivision(bounds, false);

        // Passing high above the claim leaves only its ceiling block near enough.
        CornerRaycast.CornerHit above = closestOf(claim, new Vec3(10.5D, 71.5D, 0.5D), view);
        assertNotNull(above);
        assertEquals(new BlockPos(10, 70, 10), above.pos());

        // Any further above, or below the floor, and the column is out of range.
        assertNull(closestOf(claim, new Vec3(10.5D, 72.5D, 0.5D), view));
        assertNull(closestOf(claim, new Vec3(10.5D, 58.5D, 0.5D), view));

        // The column the player looks along from behind never counts either.
        assertNull(closestOf(claim, new Vec3(10.5D, 65.5D, 20.5D), view));
    }

    @Test
    void cornersCountOnlyWithinReachOfTheViewLine()
    {
        Vec3 eye = new Vec3(0.5D, 62.5D, 0.5D);
        Vec3 view = new Vec3(1.0D, 0.0D, 0.0D);

        CornerRaycast.CornerHit near = closestOf(subdivision(ClaimBounds.rectangle(96, 62, 0, 100, 64, 0)), eye, view);
        assertNotNull(near);
        assertEquals(new BlockPos(96, 62, 0), near.pos());

        assertNull(closestOf(subdivision(ClaimBounds.rectangle(150, 62, 0, 154, 64, 0)), eye, view));
    }

    @Test
    void aClaimOneBlockTallLetsTheAimRestOnTheBlockAboveItsCorner()
    {
        Vec3 eye = new Vec3(10.5D, 62.5D, 0.5D);
        Vec3 view = new Vec3(0.0D, 0.0D, 1.0D);

        // The view line passes 1.0 block above the corner center, which alone is 2.0 away.
        CornerRaycast.CornerHit corner = closestOf(subdivision(ClaimBounds.rectangle(10, 60, 10, 14, 60, 14)), eye, view);

        assertNotNull(corner);
        assertEquals(new BlockPos(10, 60, 10), corner.pos());
    }

    @Test
    void theWorldCeilingHoldsBackTheBlockAboveACorner()
    {
        Vec3 eye = new Vec3(10.5D, 321.6D, 0.5D);
        Vec3 view = new Vec3(0.0D, 0.0D, 1.0D);

        // The view line passes 1.1 blocks above the block that would sit over the corner, which is
        // one above the world's own top and so has no block to aim at.
        assertNull(closestOf(subdivision(ClaimBounds.rectangle(10, WORLD_MAX_Y, 10, 14, WORLD_MAX_Y, 14)), eye, view));
    }

    private static CornerRaycast.CornerHit closestOf(@NotNull ClaimSnapshot claim, @NotNull Vec3 eye, @NotNull Vec3 view)
    {
        return CornerRaycast.closestCorner(
                List.of(claim), WORLD, eye, view, TOOL_REACH, WORLD_MIN_Y, WORLD_MAX_Y);
    }

    private static ClaimSnapshot subdivision(@NotNull ClaimBounds bounds)
    {
        return new ClaimSnapshot(1L, WORLD, null, null, bounds, true, true);
    }

    /** @param threeDimensional false for a claim that reaches the world's whole height */
    private static ClaimSnapshot subdivision(@NotNull ClaimBounds bounds, boolean threeDimensional)
    {
        return new ClaimSnapshot(1L, WORLD, null, null, bounds, threeDimensional, true);
    }

    private static Vec3 lookFrom(@NotNull Vec3 eye, double x, double y, double z)
    {
        return new Vec3(x - eye.x, y - eye.y, z - eye.z).normalize();
    }
}
