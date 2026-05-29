package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Chunk-aware in-memory index for platform-neutral claim snapshots.
 */
public final class ClaimSnapshotIndex
{
    private final Map<Long, ClaimSnapshot> snapshotsById = new HashMap<>();
    private final Map<String, Map<Long, Set<Long>>> chunkClaimIdsByWorld = new HashMap<>();

    public synchronized void clear()
    {
        this.snapshotsById.clear();
        this.chunkClaimIdsByWorld.clear();
    }

    public synchronized void rebuild(@NotNull Collection<ClaimSnapshot> snapshots)
    {
        clear();
        for (ClaimSnapshot snapshot : snapshots)
        {
            put(snapshot);
        }
    }

    public synchronized void put(@NotNull ClaimSnapshot snapshot)
    {
        Long id = requireId(snapshot);
        remove(id);

        this.snapshotsById.put(id, snapshot);
        Map<Long, Set<Long>> worldChunks = this.chunkClaimIdsByWorld.computeIfAbsent(
                snapshot.worldKey(),
                ignored -> new HashMap<>()
        );
        for (long chunkHash : chunkHashes(snapshot.bounds()))
        {
            worldChunks.computeIfAbsent(chunkHash, ignored -> new LinkedHashSet<>()).add(id);
        }
    }

    public synchronized @Nullable ClaimSnapshot remove(long id)
    {
        ClaimSnapshot removed = this.snapshotsById.remove(id);
        if (removed == null)
        {
            return null;
        }

        Map<Long, Set<Long>> worldChunks = this.chunkClaimIdsByWorld.get(removed.worldKey());
        if (worldChunks == null)
        {
            return removed;
        }

        for (long chunkHash : chunkHashes(removed.bounds()))
        {
            Set<Long> ids = worldChunks.get(chunkHash);
            if (ids == null)
            {
                continue;
            }

            ids.remove(id);
            if (ids.isEmpty())
            {
                worldChunks.remove(chunkHash);
            }
        }

        if (worldChunks.isEmpty())
        {
            this.chunkClaimIdsByWorld.remove(removed.worldKey());
        }

        return removed;
    }

    public synchronized @Nullable ClaimSnapshot get(long id)
    {
        return this.snapshotsById.get(id);
    }

    public synchronized @NotNull List<ClaimSnapshot> snapshots()
    {
        List<ClaimSnapshot> snapshots = new ArrayList<>(this.snapshotsById.values());
        snapshots.sort(Comparator.comparing(ClaimSnapshot::id, Comparator.nullsLast(Long::compareTo)));
        return Collections.unmodifiableList(snapshots);
    }

    public synchronized @NotNull List<ClaimSnapshot> candidates(@NotNull String worldKey, @NotNull ClaimBounds bounds)
    {
        Map<Long, Set<Long>> worldChunks = this.chunkClaimIdsByWorld.get(worldKey);
        if (worldChunks == null)
        {
            return Collections.emptyList();
        }

        Set<Long> candidateIds = new LinkedHashSet<>();
        for (long chunkHash : chunkHashes(bounds))
        {
            Set<Long> ids = worldChunks.get(chunkHash);
            if (ids != null)
            {
                candidateIds.addAll(ids);
            }
        }

        List<ClaimSnapshot> candidates = new ArrayList<>();
        for (Long id : candidateIds)
        {
            ClaimSnapshot snapshot = this.snapshotsById.get(id);
            if (snapshot != null && snapshot.bounds().intersects(bounds, true))
            {
                candidates.add(snapshot);
            }
        }
        candidates.sort(ClaimSnapshotIndex::compareBySpecificity);
        return Collections.unmodifiableList(candidates);
    }

    public synchronized @Nullable ClaimSnapshot findAt(
            @NotNull String worldKey,
            int x,
            int y,
            int z,
            boolean ignoreHeight,
            boolean ignoreSubclaims)
    {
        ClaimBounds pointBounds = ClaimBounds.rectangle(x, y, z, x, y, z);
        List<ClaimSnapshot> candidates = candidates(worldKey, pointBounds);
        ClaimSnapshot best = null;
        for (ClaimSnapshot snapshot : candidates)
        {
            if (ignoreSubclaims && snapshot.subdivision())
            {
                continue;
            }

            if (!snapshot.contains(worldKey, x, y, z, ignoreHeight))
            {
                continue;
            }

            if (best == null || compareAt(snapshot, best, y) < 0)
            {
                best = snapshot;
            }
        }

        return best;
    }

    private static int compareAt(@NotNull ClaimSnapshot first, @NotNull ClaimSnapshot second, int y)
    {
        boolean firstContainsY3D = first.threeDimensional() && first.bounds().containsY(y);
        boolean secondContainsY3D = second.threeDimensional() && second.bounds().containsY(y);
        if (firstContainsY3D != secondContainsY3D)
        {
            return firstContainsY3D ? -1 : 1;
        }

        if (firstContainsY3D)
        {
            int yCompare = Integer.compare(first.bounds().yHeight(), second.bounds().yHeight());
            if (yCompare != 0)
            {
                return yCompare;
            }
        }

        return compareBySpecificity(first, second);
    }

    private static int compareBySpecificity(@NotNull ClaimSnapshot first, @NotNull ClaimSnapshot second)
    {
        int areaCompare = Integer.compare(first.bounds().area(), second.bounds().area());
        if (areaCompare != 0)
        {
            return areaCompare;
        }

        if (first.subdivision() != second.subdivision())
        {
            return first.subdivision() ? -1 : 1;
        }

        Long firstId = first.id();
        Long secondId = second.id();
        if (Objects.equals(firstId, secondId))
        {
            return 0;
        }
        if (firstId == null)
        {
            return 1;
        }
        if (secondId == null)
        {
            return -1;
        }
        return Long.compare(firstId, secondId);
    }

    private static long requireId(@NotNull ClaimSnapshot snapshot)
    {
        Long id = snapshot.id();
        if (id == null)
        {
            throw new IllegalArgumentException("Indexed claim snapshots must have an id.");
        }

        return id;
    }

    private static @NotNull List<Long> chunkHashes(@NotNull ClaimBounds bounds)
    {
        List<Long> hashes = new ArrayList<>();
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
        {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            {
                hashes.add(chunkHash(chunkX, chunkZ));
            }
        }
        return hashes;
    }

    private static long chunkHash(long chunkX, long chunkZ)
    {
        return chunkZ ^ (chunkX << 32);
    }
}
