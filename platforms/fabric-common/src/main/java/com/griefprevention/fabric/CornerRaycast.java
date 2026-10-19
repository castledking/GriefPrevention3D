package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The extra raytrace Paper plays for the modification tool. A 3D claim's corner is often air, so a
 * click on a faraway corner comes back as no block at all, or as a block in front of the corner.
 * Following the player's view for the corner they aim at keeps those clicks on the corner.
 */
final class CornerRaycast
{
    /** How forgiving the aim around a corner may be, as on Paper. */
    private static final double THRESHOLD = 1.2D;
    /** How far past a corner a clicked block may be and still lose to it, as on Paper. */
    private static final double CLICKED_TOLERANCE = 2.0D;

    private CornerRaycast()
    {
    }

    /** A corner of a 3D claim the view line passes near, and the block it selects. */
    static final class CornerHit
    {
        private final @NotNull BlockPos pos;
        private final double distanceAlongView;

        private CornerHit(@NotNull BlockPos pos, double distanceAlongView)
        {
            this.pos = pos;
            this.distanceAlongView = distanceAlongView;
        }

        @NotNull BlockPos pos()
        {
            return this.pos;
        }

        double distanceAlongView()
        {
            return this.distanceAlongView;
        }
    }

    /**
     * @return the closest corner of a claim of {@code worldKey} the view line passes near, or null
     *         when it passes by every corner
     */
    static @Nullable CornerHit closestCorner(
            @NotNull List<ClaimSnapshot> claims,
            @NotNull String worldKey,
            @NotNull Vec3 eye,
            @NotNull Vec3 view,
            double maxDistance,
            int worldMinY,
            int worldMaxY)
    {
        Closest closest = new Closest();
        for (ClaimSnapshot claim : claims)
        {
            if (!claim.worldKey().equals(worldKey))
            {
                continue;
            }

            ClaimBounds bounds = claim.bounds();
            if (bounds.isShaped())
            {
                for (OrthogonalPoint2i corner : bounds.polygon().corners())
                {
                    considerCornerPillar(closest, eye, view, maxDistance, bounds,
                            corner.x(), corner.z(), corner.x(), corner.z());
                }
                continue;
            }
            for (int x : new int[] { bounds.minX(), bounds.maxX() })
            {
                for (int z : new int[] { bounds.minZ(), bounds.maxZ() })
                {
                    if (claim.threeDimensional())
                    {
                        for (int y : new int[] { bounds.minY(), bounds.maxY() })
                        {
                            considerCorner(closest, eye, view, maxDistance, worldMinY, worldMaxY, bounds, x, y, z);
                        }
                    }
                    else
                    {
                        // A claim without a height of its own has corners that run its whole height,
                        // so whichever block of that corner the view line passes is the one clicked.
                        considerCornerPillar(closest, eye, view, maxDistance, bounds, x, z, x, z);
                        if (bounds.maxX() - bounds.minX() >= 1)
                        {
                            int insideX = x == bounds.minX() ? x + 1 : x - 1;
                            considerCornerPillar(closest, eye, view, maxDistance, bounds, insideX, z, x, z);
                        }
                        if (bounds.maxZ() - bounds.minZ() >= 1)
                        {
                            int insideZ = z == bounds.minZ() ? z + 1 : z - 1;
                            considerCornerPillar(closest, eye, view, maxDistance, bounds, x, insideZ, x, z);
                        }
                    }
                }
            }
        }

        return closest.toHit();
    }

    /**
     * A corner beats the block the click reported when that block lies behind the player, or within
     * {@value #CLICKED_TOLERANCE} blocks of the corner along the view line. A click on nothing
     * always loses to the corner, which may be the very air the player aimed at.
     */
    static boolean beatsClickedBlock(
            @NotNull Vec3 eye,
            @NotNull Vec3 view,
            @Nullable BlockPos clicked,
            double cornerDistanceAlongView)
    {
        if (clicked == null)
        {
            return true;
        }

        Vec3 clickedCenter = new Vec3(clicked.getX() + 0.5D, clicked.getY() + 0.5D, clicked.getZ() + 0.5D);
        double clickedDistanceAlongView = clickedCenter.subtract(eye).dot(view);
        return clickedDistanceAlongView < 0 || cornerDistanceAlongView <= clickedDistanceAlongView + CLICKED_TOLERANCE;
    }

    /**
     * The custom corner raycast must not replace a vanilla target within interaction reach. Checking the distance
     * along the view as well as the corner block's center covers grazing rays whose center falls just outside the
     * vanilla radius while the aimed-at point remains inside it.
     */
    static boolean withinVanillaReach(@NotNull Vec3 eye, @NotNull CornerHit corner, double vanillaReach)
    {
        return corner.distanceAlongView() <= vanillaReach
                || eye.distanceToSqr(Vec3.atCenterOf(corner.pos())) <= vanillaReach * vanillaReach;
    }

    /**
     * Aims at a corner that runs the whole height of a claim, which is what a claim without a
     * height of its own has. The height at which the view line passes the corner decides which of
     * its blocks the click lands on, so a corner under water, in the air or behind a gap is as
     * good as any other.
     *
     * @param pillarX the column of blocks tested, which may sit a block off the corner
     * @param cornerX the corner block the click lands on
     */
    private static void considerCornerPillar(
            @NotNull Closest closest,
            @NotNull Vec3 eye,
            @NotNull Vec3 view,
            double maxDistance,
            @NotNull ClaimBounds bounds,
            int pillarX,
            int pillarZ,
            int cornerX,
            int cornerZ)
    {
        double centerX = pillarX + 0.5D;
        double centerZ = pillarZ + 0.5D;
        double distanceAlongView;
        double off;
        int cornerY;
        double aboveOrBelow;
        if (Math.abs(view.x) + Math.abs(view.z) < 1.0e-12D)
        {
            // Looking straight up or down keeps the eye's own height and leaves the column.
            distanceAlongView = 0.0D;
            off = Math.hypot(eye.x - centerX, eye.z - centerZ);
            cornerY = clampToHeight(eye.y, bounds);
            aboveOrBelow = Math.abs(eye.y - (cornerY + 0.5D));
        }
        else
        {
            // Where the view line passes the column, and how far off the column it passes.
            Vec3 flat = new Vec3(view.x, 0.0D, view.z);
            distanceAlongView = ((centerX - eye.x) * flat.x + (centerZ - eye.z) * flat.z) / flat.lengthSqr();
            off = Math.abs((centerX - eye.x) * flat.z - (centerZ - eye.z) * flat.x) / flat.length();
            double heightAtCorner = eye.y + distanceAlongView * view.y;
            cornerY = clampToHeight(heightAtCorner, bounds);
            aboveOrBelow = Math.abs(heightAtCorner - (cornerY + 0.5D));
        }

        if (distanceAlongView < 0 || distanceAlongView > maxDistance)
        {
            return;
        }
        if (Math.hypot(off, aboveOrBelow) > THRESHOLD)
        {
            return;
        }

        closest.offer(new BlockPos(cornerX, cornerY, cornerZ), distanceAlongView);
    }

    /** The block of a claim a height belongs to, or the one at its edge when it is past them. */
    private static int clampToHeight(double y, @NotNull ClaimBounds bounds)
    {
        return (int) Math.floor(Math.max(bounds.minY(), Math.min(bounds.maxY(), y)));
    }

    /**
     * Aims at the corner itself, but also at the block one step inside the claim along each axis,
     * so a corner behind a wall or above a floor still catches the click. A claim one block wide
     * has no such interior, and one one block tall leans on the world's own limits instead.
     */
    private static void considerCorner(
            @NotNull Closest closest,
            @NotNull Vec3 eye,
            @NotNull Vec3 view,
            double maxDistance,
            int worldMinY,
            int worldMaxY,
            @NotNull ClaimBounds bounds,
            int cornerX,
            int cornerY,
            int cornerZ)
    {
        offer(closest, eye, view, maxDistance, cornerX, cornerY, cornerZ, cornerX, cornerY, cornerZ);

        if (bounds.maxX() - bounds.minX() >= 1)
        {
            int insideX = cornerX == bounds.minX() ? cornerX + 1 : cornerX - 1;
            offer(closest, eye, view, maxDistance, insideX, cornerY, cornerZ, cornerX, cornerY, cornerZ);
        }

        if (bounds.maxZ() - bounds.minZ() >= 1)
        {
            int insideZ = cornerZ == bounds.minZ() ? cornerZ + 1 : cornerZ - 1;
            offer(closest, eye, view, maxDistance, cornerX, cornerY, insideZ, cornerX, cornerY, cornerZ);
        }

        if (bounds.maxY() - bounds.minY() >= 1)
        {
            int insideY = cornerY == bounds.minY() ? cornerY + 1 : cornerY - 1;
            offer(closest, eye, view, maxDistance, cornerX, insideY, cornerZ, cornerX, cornerY, cornerZ);
        }

        if (bounds.maxY() == bounds.minY())
        {
            if (cornerY + 1 <= worldMaxY)
            {
                offer(closest, eye, view, maxDistance, cornerX, cornerY + 1, cornerZ, cornerX, cornerY, cornerZ);
            }
            if (cornerY - 1 >= worldMinY)
            {
                offer(closest, eye, view, maxDistance, cornerX, cornerY - 1, cornerZ, cornerX, cornerY, cornerZ);
            }
        }
    }

    /**
     * @param targetX the block center the view line is tested against, which may sit a block off
     *                the corner
     * @param cornerX the corner block the click lands on
     */
    private static void offer(
            @NotNull Closest closest,
            @NotNull Vec3 eye,
            @NotNull Vec3 view,
            double maxDistance,
            int targetX,
            int targetY,
            int targetZ,
            int cornerX,
            int cornerY,
            int cornerZ)
    {
        Vec3 target = new Vec3(targetX + 0.5D, targetY + 0.5D, targetZ + 0.5D);
        Vec3 toTarget = target.subtract(eye);
        double distanceAlongView = toTarget.dot(view);
        if (distanceAlongView < 0 || distanceAlongView > maxDistance)
        {
            return;
        }

        Vec3 closestPoint = eye.add(view.scale(distanceAlongView));
        if (closestPoint.distanceToSqr(target) > THRESHOLD * THRESHOLD)
        {
            return;
        }

        closest.offer(new BlockPos(cornerX, cornerY, cornerZ), distanceAlongView);
    }

    /** The corner found so far, closest along the view line. */
    private static final class Closest
    {
        private @Nullable BlockPos pos;
        private double distanceAlongView = Double.POSITIVE_INFINITY;

        private void offer(@NotNull BlockPos pos, double distanceAlongView)
        {
            if (distanceAlongView < this.distanceAlongView)
            {
                this.pos = pos;
                this.distanceAlongView = distanceAlongView;
            }
        }

        private @Nullable CornerHit toHit()
        {
            return this.pos == null ? null : new CornerHit(this.pos, this.distanceAlongView);
        }
    }
}
