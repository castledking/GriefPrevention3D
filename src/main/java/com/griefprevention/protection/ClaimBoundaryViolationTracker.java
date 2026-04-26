package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks piston and liquid boundary violations per claim owner to prevent
 * spamming the same warning repeatedly. Violations within a spatial radius
 * are treated as duplicates and suppressed.
 *
 * <p>Burst detection condenses rapid sequential violations of the same type
 * into a single summary message so the notification stays helpful, not noisy.</p>
 */
public class ClaimBoundaryViolationTracker
{
    private static ClaimBoundaryViolationTracker instance;

    /**
     * Get the singleton instance of the tracker.
     * @return the tracker instance
     */
    public static synchronized ClaimBoundaryViolationTracker getInstance()
    {
        if (instance == null)
        {
            instance = new ClaimBoundaryViolationTracker();
        }
        return instance;
    }

    /**
     * Reset the singleton instance. Used primarily for testing.
     */
    public static synchronized void resetInstance()
    {
        if (instance != null)
        {
            instance.clearAll();
        }
        instance = null;
    }
    /**
     * Types of boundary violations that can be tracked.
     */
    public enum ViolationType
    {
        PISTON,
        LIQUID
    }

    /**
     * Direction of the boundary violation - whether it's trying to enter or leave a claim.
     */
    public enum ViolationDirection
    {
        EXTERNAL, // Something trying to enter the claim (from outside)
        INTERNAL  // Something trying to leave the claim (from inside)
    }

    /**
     * A recorded violation with location and timestamp.
     */
    private record TrackedViolation(int x, int y, int z, @NotNull String worldName,
                                    @NotNull ViolationType type, @NotNull ViolationDirection direction,
                                    int radius, long timestamp)
    {
        boolean covers(int ox, int oy, int oz, @NotNull String oWorld,
                       @NotNull ViolationType oType, @NotNull ViolationDirection oDirection)
        {
            if (oType != type) return false;
            if (oDirection != direction) return false;
            if (!oWorld.equals(worldName)) return false;
            return Math.abs(ox - x) <= radius
                    && Math.abs(oy - y) <= radius
                    && Math.abs(oz - z) <= radius;
        }
    }

    /**
     * Pending burst violations waiting to be flushed as a summary.
     */
    private record PendingBurst(@NotNull ViolationType type, @NotNull ViolationDirection direction,
                                @NotNull String worldName, int count,
                                int firstX, int firstY, int firstZ, long firstTimestamp)
    {
    }

    // Per-owner tracked violations (keyed by owner UUID)
    private final Map<UUID, Deque<TrackedViolation>> violations = new ConcurrentHashMap<>();

    // Per-owner pending burst state
    private final Map<UUID, PendingBurst> pendingBursts = new ConcurrentHashMap<>();

    // How long (ms) a tracked violation stays relevant before expiring
    private static final long VIOLATION_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    // Maximum violations tracked per owner before oldest are pruned
    private static final int MAX_VIOLATIONS_PER_OWNER = 200;

    // Burst window: violations arriving within this period are batched into one message
    private static final long BURST_WINDOW_MS = 2000; // 2 seconds

    // Dedup radius for pistons (exact location only)
    private static final int PISTON_RADIUS = 0;

    // Dedup radius for water (8 blocks in each direction)
    private static final int WATER_RADIUS = 8;

    // Dedup radius for lava in overworld/end (5 blocks)
    private static final int LAVA_OVERWORLD_RADIUS = 5;

    // Dedup radius for lava in nether (8 blocks, since nether lava flows 8 blocks)
    private static final int LAVA_NETHER_RADIUS = 8;

    /**
     * Attempt to record a piston boundary violation and notify the claim owner.
     *
     * @param ownerID   the claim owner's UUID, or null for admin claims
     * @param location  the violation location
     * @param direction whether the violation is external (into claim) or internal (out of claim)
     */
    public void trackPistonViolation(@Nullable UUID ownerID, @NotNull Location location,
                                      @NotNull ViolationDirection direction)
    {
        if (ownerID == null) return;

        String worldName = location.getWorld() != null ? location.getWorld().getName() : "";
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        if (isDuplicate(ownerID, x, y, z, worldName, ViolationType.PISTON, direction)) return;

        recordViolation(ownerID, x, y, z, worldName, ViolationType.PISTON, direction, PISTON_RADIUS);
        queueOrFlushBurst(ownerID, ViolationType.PISTON, direction, worldName, x, y, z);
    }

    /**
     * Attempt to record a liquid boundary violation and notify the claim owner.
     *
     * @param ownerID   the claim owner's UUID, or null for admin claims
     * @param location  the violation location
     * @param isLava    true if the liquid is lava, false for water
     * @param world     the world, used to determine dedup radius
     * @param direction whether the violation is external (into claim) or internal (out of claim)
     */
    public void trackLiquidViolation(@Nullable UUID ownerID, @NotNull Location location,
                                      boolean isLava, @NotNull World world,
                                      @NotNull ViolationDirection direction)
    {
        if (ownerID == null) return;

        String worldName = world.getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        int radius;
        if (isLava)
        {
            // Nether lava flows 8 blocks, so use 8-block radius for dedup there.
            // In overworld/end, lava only flows ~4 blocks, use 5-block radius.
            radius = (world.getEnvironment() == World.Environment.NETHER)
                    ? LAVA_NETHER_RADIUS
                    : LAVA_OVERWORLD_RADIUS;
        }
        else
        {
            radius = WATER_RADIUS;
        }

        if (isDuplicate(ownerID, x, y, z, worldName, ViolationType.LIQUID, direction)) return;

        recordViolation(ownerID, x, y, z, worldName, ViolationType.LIQUID, direction, radius);
        queueOrFlushBurst(ownerID, ViolationType.LIQUID, direction, worldName, x, y, z);
    }

    /**
     * Check if a violation at this location is already covered by an existing tracked violation.
     */
    private boolean isDuplicate(@NotNull UUID ownerID, int x, int y, int z,
                                @NotNull String worldName, @NotNull ViolationType type,
                                @NotNull ViolationDirection direction)
    {
        Deque<TrackedViolation> ownerViolations = violations.get(ownerID);
        if (ownerViolations == null) return false;

        long now = System.currentTimeMillis();

        synchronized (ownerViolations)
        {
            for (TrackedViolation v : ownerViolations)
            {
                if (now - v.timestamp() > VIOLATION_EXPIRY_MS) continue;
                if (v.covers(x, y, z, worldName, type, direction)) return true;
            }
        }

        return false;
    }

    /**
     * Record a new violation for the given owner.
     */
    private void recordViolation(@NotNull UUID ownerID, int x, int y, int z,
                                 @NotNull String worldName, @NotNull ViolationType type,
                                 @NotNull ViolationDirection direction, int radius)
    {
        Deque<TrackedViolation> ownerViolations = violations.computeIfAbsent(ownerID,
                k -> new ArrayDeque<>());

        long now = System.currentTimeMillis();

        synchronized (ownerViolations)
        {
            // Prune expired entries
            Iterator<TrackedViolation> it = ownerViolations.iterator();
            while (it.hasNext())
            {
                if (now - it.next().timestamp() > VIOLATION_EXPIRY_MS)
                {
                    it.remove();
                }
            }

            // Prune oldest if over capacity
            while (ownerViolations.size() >= MAX_VIOLATIONS_PER_OWNER)
            {
                ownerViolations.pollFirst();
            }

            ownerViolations.addLast(new TrackedViolation(x, y, z, worldName, type, direction, radius, now));
        }
    }

    /**
     * Either queue a burst or flush and send the notification.
     *
     * <p>If another violation of the same type and direction arrived very recently, we increment
     * the burst counter instead of sending immediately. A delayed task then
     * flushes the burst as a single consolidated message.</p>
     */
    private void queueOrFlushBurst(@NotNull UUID ownerID, @NotNull ViolationType type,
                                   @NotNull ViolationDirection direction,
                                   @NotNull String worldName, int x, int y, int z)
    {
        long now = System.currentTimeMillis();

        PendingBurst existing = pendingBursts.get(ownerID);
        if (existing != null && existing.type() == type && existing.direction() == direction
                && existing.worldName().equals(worldName)
                && (now - existing.firstTimestamp()) < BURST_WINDOW_MS)
        {
            // Still inside the burst window – just bump the counter
            pendingBursts.put(ownerID, new PendingBurst(
                    type, direction, worldName, existing.count() + 1,
                    existing.firstX(), existing.firstY(), existing.firstZ(),
                    existing.firstTimestamp()));
            return;
        }

        // No active burst – start a new one and schedule the flush
        pendingBursts.put(ownerID, new PendingBurst(type, direction, worldName, 1, x, y, z, now));

        // We use global scheduler so the message is delivered on the tick thread (or main thread on Spigot).
        SchedulerUtil.runLaterGlobal(
                GriefPrevention.instance,
                () -> flushBurst(ownerID),
                // Convert burst window to ticks (20 ticks/sec), add 1 for safety
                (BURST_WINDOW_MS / 50) + 1
        );
    }

    /**
     * Flush the pending burst for an owner, sending a single consolidated message.
     */
    private void flushBurst(@NotNull UUID ownerID)
    {
        PendingBurst burst = pendingBursts.remove(ownerID);
        if (burst == null) return;

        Player player = Bukkit.getPlayer(ownerID);
        if (player == null || !player.isOnline()) return;

        String typeLabel = switch (burst.type())
        {
            case PISTON -> "piston";
            case LIQUID -> "liquid";
        };

        if (burst.count() == 1)
        {
            // Single violation – specific location message based on type and direction
            Messages messageKey;
            if (burst.type() == ViolationType.PISTON)
            {
                messageKey = burst.direction() == ViolationDirection.EXTERNAL
                        ? Messages.ExternalPistonBoundaryViolation
                        : Messages.InternalPistonBoundaryViolation;
            }
            else
            {
                messageKey = burst.direction() == ViolationDirection.EXTERNAL
                        ? Messages.ExternalLiquidBoundaryViolation
                        : Messages.InternalLiquidBoundaryViolation;
            }
            String message = GriefPrevention.instance.dataStore.getMessage(
                    messageKey,
                    String.valueOf(burst.firstX()),
                    String.valueOf(burst.firstY()),
                    String.valueOf(burst.firstZ())
            );
            player.sendMessage(message);
        }
        else
        {
            // Multiple violations condensed into one summary
            String message = GriefPrevention.instance.dataStore.getMessage(
                    Messages.BoundaryViolationBurstSummary,
                    String.valueOf(burst.count()),
                    typeLabel,
                    String.valueOf(burst.firstX()),
                    String.valueOf(burst.firstY()),
                    String.valueOf(burst.firstZ())
            );
            player.sendMessage(message);
        }
    }

    /**
     * Clear all tracked violations for a specific owner. Called when an owner
     * goes offline or when violations should be reset.
     */
    public void clearOwner(@NotNull UUID ownerID)
    {
        violations.remove(ownerID);
        pendingBursts.remove(ownerID);
    }

    /**
     * Clear all tracked violations globally. Useful on plugin disable.
     */
    public void clearAll()
    {
        violations.clear();
        pendingBursts.clear();
    }
}
