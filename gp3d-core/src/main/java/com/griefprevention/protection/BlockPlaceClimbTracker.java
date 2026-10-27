package com.griefprevention.protection;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Short-lived movement correction for a rejected block that could support the player's feet. */
public final class BlockPlaceClimbTracker
{
    private static final double HALF_WIDTH = 0.299;
    private static final long CHECKPOINT_MAX_AGE_MILLIS = 2000;
    private static final long CORRECTION_WINDOW_MILLIS = 750;
    private @Nullable Position supported;
    private long supportedAt;
    private @Nullable Position pending;
    private long pendingUntil;
    private @Nullable Position climbableCheckpoint;
    private final List<BlockPoint> rejectedClimbables = new ArrayList<>();
    private long climbableUntil;
    private boolean touchedClimbable;

    public static final class Position
    {
        public final double x;
        public final double y;
        public final double z;

        public Position(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Box
    {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
        {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean overlapsFeet(Position position)
        {
            return position.x + HALF_WIDTH > minX && position.x - HALF_WIDTH < maxX
                    && position.z + HALF_WIDTH > minZ && position.z - HALF_WIDTH < maxZ;
        }

        public boolean intersects(Box other)
        {
            return maxX > other.minX + 1.0e-7 && minX < other.maxX - 1.0e-7
                    && maxY > other.minY + 1.0e-7 && minY < other.maxY - 1.0e-7
                    && maxZ > other.minZ + 1.0e-7 && minZ < other.maxZ - 1.0e-7;
        }
    }

    public void recordSupported(Position position, long nowMillis)
    {
        if (pending != null && nowMillis <= pendingUntil && position.y > pending.y + 0.05) return;
        if (hasClimbableAttempt(nowMillis) && position.y > climbableCheckpoint.y + 0.05) return;
        supported = position;
        supportedAt = nowMillis;
    }

    public @Nullable Position denyPlacement(Position current, List<Box> collision, long nowMillis)
    {
        if (supported == null || nowMillis - supportedAt < 0
                || nowMillis - supportedAt > CHECKPOINT_MAX_AGE_MILLIS || !nearby(supported, current)) return null;
        for (Box box : collision)
        {
            if (box.overlapsFeet(current) && current.y >= box.minY - 0.05 && current.y <= box.maxY + 1.5)
            {
                pending = supported;
                pendingUntil = nowMillis + CORRECTION_WINDOW_MILLIS;
                return setback(current, nowMillis);
            }
        }
        return null;
    }

    public @Nullable Position setback(Position current, long nowMillis)
    {
        if (pending != null)
        {
            if (nowMillis > pendingUntil || !nearby(pending, current)) pending = null;
            else if (current.y > pending.y + 0.05)
            {
                Position result = pending;
                pending = null;
                allowRealClimbing();
                return result;
            }
        }
        if (!hasClimbableAttempt(nowMillis)) return null;
        if (!nearby(climbableCheckpoint, current))
        {
            allowRealClimbing();
            return null;
        }
        for (BlockPoint block : rejectedClimbables)
        {
            // Vanilla checks the block containing the feet, not the ladder's collision shape.
            if (Math.floor(current.x) == block.x() && Math.floor(current.y) == block.y()
                    && Math.floor(current.z) == block.z()) touchedClimbable = true;
        }
        if (!touchedClimbable || current.y <= climbableCheckpoint.y + 0.05) return null;
        Position result = climbableCheckpoint;
        allowRealClimbing();
        return result;
    }

    public @Nullable Position denyClimbablePlacement(Position current, List<BlockPoint> climbables, long nowMillis)
    {
        if (supported == null || nowMillis - supportedAt < 0
                || nowMillis - supportedAt > CHECKPOINT_MAX_AGE_MILLIS || !nearby(supported, current)) return null;
        hasClimbableAttempt(nowMillis); // Discard an expired attempt before considering another placement.
        for (BlockPoint block : climbables)
        {
            if (Math.abs(current.x - (block.x() + 0.5)) > 1.5
                    || Math.abs(current.z - (block.z() + 0.5)) > 1.5
                    || Math.abs(Math.floor(current.y) - block.y()) > 1) continue;
            if (climbableCheckpoint == null) climbableCheckpoint = supported;
            climbableUntil = nowMillis + CORRECTION_WINDOW_MILLIS;
            if (!rejectedClimbables.contains(block))
            {
                if (rejectedClimbables.size() == 32) rejectedClimbables.remove(0);
                rejectedClimbables.add(block);
            }
        }
        return setback(current, nowMillis);
    }

    public boolean hasClimbableAttempt(long nowMillis)
    {
        if (climbableCheckpoint != null && nowMillis > climbableUntil) allowRealClimbing();
        return climbableCheckpoint != null;
    }

    /** A real server-side climbable can explain the movement without a client-predicted ladder. */
    public void allowRealClimbing()
    {
        climbableCheckpoint = null;
        rejectedClimbables.clear();
        touchedClimbable = false;
    }

    public void clear()
    {
        supported = null;
        pending = null;
        allowRealClimbing();
    }

    private static boolean nearby(Position anchor, Position position)
    {
        double dx = anchor.x - position.x;
        double dz = anchor.z - position.z;
        return dx * dx + dz * dz <= 16 && position.y >= anchor.y - 0.05 && position.y <= anchor.y + 3;
    }
}
