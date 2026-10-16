package com.griefprevention.protection;

import com.griefprevention.messages.MessageKey;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The warnings a claim owner gets when a piston or a fluid is stopped at their claim's border, as
 * Paper's {@code ClaimBoundaryViolationTracker} sends them. A stopped piston or fluid keeps trying
 * every tick, so repeats near a place already reported stay quiet for five minutes, and a burst of
 * new ones within two seconds is reported as one summary.
 *
 * <p>The caller supplies the time, records violations as they are refused, and delivers whatever
 * {@link #due(long)} hands back.
 */
public final class BoundaryViolationAlerts
{
    /** A piston is reported once per block. */
    public static final int PISTON_RADIUS = 0;
    /** Water flows up to eight blocks. */
    public static final int WATER_RADIUS = 8;
    /** Lava flows about four blocks in the overworld and the end. */
    public static final int LAVA_RADIUS = 5;
    /** Lava flows eight blocks in the nether. */
    public static final int NETHER_LAVA_RADIUS = 8;

    static final long EXPIRY_MILLIS = 5L * 60L * 1000L;
    static final long BURST_WINDOW_MILLIS = 2000L;
    static final int MAX_TRACKED_PER_OWNER = 200;

    /** What crossed the border. */
    public enum Kind
    {
        PISTON("piston"),
        LIQUID("liquid");

        private final @NotNull String label;

        Kind(@NotNull String label)
        {
            this.label = label;
        }

        /** The word Paper puts in the burst summary. */
        public @NotNull String label()
        {
            return this.label;
        }
    }

    /** Which way it tried to cross. */
    public enum Direction
    {
        /** Into the claim from outside. */
        EXTERNAL,
        /** Out of the claim. */
        INTERNAL
    }

    private final Map<UUID, Deque<Tracked>> tracked = new HashMap<>();
    private final List<Burst> pending = new ArrayList<>();

    /** @return how far apart two fluid violations must be to be reported separately */
    public static int liquidRadius(boolean lava, boolean nether)
    {
        if (!lava)
        {
            return WATER_RADIUS;
        }
        return nether ? NETHER_LAVA_RADIUS : LAVA_RADIUS;
    }

    /**
     * Notes a refused piston move or fluid flow.
     *
     * @param owner whoever owns the claim it concerns
     * @param radius how close a later violation of the same kind must be to count as this one again
     * @param now the current time in milliseconds
     */
    public synchronized void record(
            @NotNull UUID owner,
            @NotNull String world,
            int x,
            int y,
            int z,
            @NotNull Kind kind,
            @NotNull Direction direction,
            int radius,
            long now)
    {
        Deque<Tracked> ownerViolations = this.tracked.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
        Iterator<Tracked> iterator = ownerViolations.iterator();
        while (iterator.hasNext())
        {
            Tracked violation = iterator.next();
            if (now - violation.timestamp > EXPIRY_MILLIS)
            {
                iterator.remove();
            }
            else if (violation.covers(world, x, y, z, kind, direction))
            {
                return;
            }
        }
        while (ownerViolations.size() >= MAX_TRACKED_PER_OWNER)
        {
            ownerViolations.pollFirst();
        }
        ownerViolations.addLast(new Tracked(world, x, y, z, kind, direction, radius, now));

        for (Burst burst : this.pending)
        {
            if (burst.owner.equals(owner) && burst.kind == kind && burst.direction == direction
                    && burst.world.equals(world))
            {
                burst.count++;
                return;
            }
        }
        this.pending.add(new Burst(owner, world, x, y, z, kind, direction, now));
    }

    /** @return the bursts whose window has closed, now ready to send; they are forgotten here */
    public synchronized @NotNull List<Burst> due(long now)
    {
        List<Burst> ready = new ArrayList<>();
        Iterator<Burst> iterator = this.pending.iterator();
        while (iterator.hasNext())
        {
            Burst burst = iterator.next();
            if (now - burst.firstTimestamp >= BURST_WINDOW_MILLIS)
            {
                iterator.remove();
                ready.add(burst);
            }
        }
        return ready;
    }

    /** Drops everything tracked for an owner, as when they leave. */
    public synchronized void forget(@NotNull UUID owner)
    {
        this.tracked.remove(owner);
        this.pending.removeIf(burst -> burst.owner.equals(owner));
    }

    /** One warning to send: a single violation, or a count of them near the first. */
    public static final class Burst
    {
        private final @NotNull UUID owner;
        private final @NotNull String world;
        private final int x;
        private final int y;
        private final int z;
        private final @NotNull Kind kind;
        private final @NotNull Direction direction;
        private final long firstTimestamp;
        private int count = 1;

        private Burst(
                @NotNull UUID owner,
                @NotNull String world,
                int x,
                int y,
                int z,
                @NotNull Kind kind,
                @NotNull Direction direction,
                long firstTimestamp)
        {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.direction = direction;
            this.firstTimestamp = firstTimestamp;
        }

        public @NotNull UUID owner()
        {
            return this.owner;
        }

        public @NotNull String world()
        {
            return this.world;
        }

        public int count()
        {
            return this.count;
        }

        /** The message Paper sends for this burst. */
        public @NotNull MessageKey message()
        {
            if (this.count > 1)
            {
                return MessageKey.BOUNDARY_VIOLATION_BURST_SUMMARY;
            }
            if (this.kind == Kind.PISTON)
            {
                return this.direction == Direction.EXTERNAL
                        ? MessageKey.EXTERNAL_PISTON_BOUNDARY_VIOLATION
                        : MessageKey.INTERNAL_PISTON_BOUNDARY_VIOLATION;
            }
            return this.direction == Direction.EXTERNAL
                    ? MessageKey.EXTERNAL_LIQUID_BOUNDARY_VIOLATION
                    : MessageKey.INTERNAL_LIQUID_BOUNDARY_VIOLATION;
        }

        /** The arguments {@link #message()} takes. */
        public @NotNull String @NotNull [] arguments()
        {
            String[] position = {String.valueOf(this.x), String.valueOf(this.y), String.valueOf(this.z)};
            if (this.count == 1)
            {
                return position;
            }
            return new String[] {String.valueOf(this.count), this.kind.label(), position[0], position[1], position[2]};
        }
    }

    private static final class Tracked
    {
        private final @NotNull String world;
        private final int x;
        private final int y;
        private final int z;
        private final @NotNull Kind kind;
        private final @NotNull Direction direction;
        private final int radius;
        private final long timestamp;

        private Tracked(
                @NotNull String world,
                int x,
                int y,
                int z,
                @NotNull Kind kind,
                @NotNull Direction direction,
                int radius,
                long timestamp)
        {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.direction = direction;
            this.radius = radius;
            this.timestamp = timestamp;
        }

        private boolean covers(
                @NotNull String otherWorld,
                int otherX,
                int otherY,
                int otherZ,
                @NotNull Kind otherKind,
                @NotNull Direction otherDirection)
        {
            return otherKind == this.kind
                    && otherDirection == this.direction
                    && otherWorld.equals(this.world)
                    && Math.abs(otherX - this.x) <= this.radius
                    && Math.abs(otherY - this.y) <= this.radius
                    && Math.abs(otherZ - this.z) <= this.radius;
        }
    }
}
