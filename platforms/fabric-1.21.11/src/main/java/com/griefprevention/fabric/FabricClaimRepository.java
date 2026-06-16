package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimRepository;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimSnapshotIndex;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FabricClaimRepository implements ClaimRepository
{
    private final ClaimSnapshotIndex claimIndex = new ClaimSnapshotIndex();
    private final Map<Long, ClaimTrustSnapshot> trustByClaimId = new LinkedHashMap<>();
    private final Path dataFolder;
    private final Logger logger;
    private long nextClaimId = 1L;

    FabricClaimRepository(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        this.dataFolder = dataFolder;
        this.logger = logger;
        reload();
    }

    synchronized int reload()
    {
        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(this.dataFolder, logger);
        this.claimIndex.rebuild(loaded.snapshots());
        this.trustByClaimId.clear();
        this.trustByClaimId.putAll(loaded.trustByClaimId());
        this.nextClaimId = loaded.nextClaimId();
        logger.info("Loaded {} native Fabric claims from {}.", loaded.snapshots().size(), this.dataFolder);
        return loaded.snapshots().size();
    }

    synchronized int claimCount()
    {
        return this.claimIndex.snapshots().size();
    }

    synchronized @NotNull List<ClaimSnapshot> snapshots()
    {
        return this.claimIndex.snapshots();
    }

    @NotNull Path dataFolder()
    {
        return this.dataFolder;
    }

    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull ServerLevel level,
            @NotNull BlockPos center,
            @NotNull UUID ownerId,
            int radius,
            @Nullable ServerPlayer player)
            throws IOException
    {
        return createClaim(
                level,
                new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius),
                new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius),
                ownerId,
                player);
    }

    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull ServerLevel level,
            @NotNull BlockPos firstCorner,
            @NotNull BlockPos secondCorner,
            @NotNull UUID ownerId,
            @Nullable ServerPlayer player)
            throws IOException
    {
        ClaimBounds bounds = ClaimBounds.rectangle(
                firstCorner.getX(),
                level.getMinY(),
                firstCorner.getZ(),
                secondCorner.getX(),
                level.getMaxY(),
                secondCorner.getZ()
        );
        ClaimSnapshot snapshot = new ClaimSnapshot(
                this.nextClaimId,
                worldKey(level),
                ownerId,
                null,
                bounds,
                false,
                false
        );

        for (ClaimSnapshot candidate : this.claimIndex.candidates(snapshot.worldKey(), snapshot.bounds()))
        {
            if (snapshot.overlaps(candidate))
            {
                return CreateClaimResult.overlap(candidate);
            }
        }

        List<ClaimSnapshot> snapshots = mutableSnapshots();
        Map<Long, ClaimTrustSnapshot> trust = mutableTrust();
        snapshots.add(snapshot);
        trust.put(snapshot.id(), ClaimTrustSnapshot.empty(ownerId));
        long previousNextClaimId = this.nextClaimId;
        this.nextClaimId = Math.max(this.nextClaimId + 1L, snapshot.id() + 1L);
        try
        {
            replaceAndSave(snapshots, trust);
        }
        catch (IOException e)
        {
            this.nextClaimId = previousNextClaimId;
            throw e;
        }
        ClaimCreatedCallback.EVENT.invoker().onClaimCreated(snapshot, player);
        return CreateClaimResult.created(snapshot);
    }

    synchronized @NotNull UpdateClaimResult updateClaimBounds(
            long claimId,
            @NotNull ClaimBounds bounds,
            @Nullable ServerPlayer player)
            throws IOException
    {
        ClaimSnapshot existing = null;
        for (ClaimSnapshot snapshot : this.claimIndex.snapshots())
        {
            if (Long.valueOf(claimId).equals(snapshot.id()))
            {
                existing = snapshot;
                break;
            }
        }
        if (existing == null)
        {
            return UpdateClaimResult.missingResult();
        }

        ClaimSnapshot updated = new ClaimSnapshot(
                existing.id(),
                existing.worldKey(),
                existing.ownerId(),
                existing.parentId(),
                bounds,
                existing.threeDimensional(),
                existing.subdivision()
        );

        for (ClaimSnapshot candidate : this.claimIndex.candidates(updated.worldKey(), updated.bounds()))
        {
            if (Long.valueOf(claimId).equals(candidate.id()))
            {
                continue;
            }
            if (updated.overlaps(candidate))
            {
                return UpdateClaimResult.overlap(candidate);
            }
        }

        List<ClaimSnapshot> snapshots = mutableSnapshots();
        for (int i = 0; i < snapshots.size(); i++)
        {
            if (Long.valueOf(claimId).equals(snapshots.get(i).id()))
            {
                snapshots.set(i, updated);
                break;
            }
        }

        replaceAndSave(snapshots, mutableTrust());
        ClaimModifiedCallback.EVENT.invoker().onClaimModified(existing, updated, player);
        return UpdateClaimResult.updated(updated);
    }

    synchronized @Nullable ClaimSnapshot deleteClaimAt(@NotNull ServerLevel level, @NotNull BlockPos pos, @Nullable ServerPlayer player)
            throws IOException
    {
        ClaimSnapshot claim = findClaimAt(level, pos);
        if (claim == null || claim.id() == null)
        {
            return null;
        }

        List<ClaimSnapshot> snapshots = mutableSnapshots();
        snapshots.removeIf(snapshot -> claim.id().equals(snapshot.id()));
        Map<Long, ClaimTrustSnapshot> trust = mutableTrust();
        trust.remove(claim.id());
        replaceAndSave(snapshots, trust);
        ClaimDeletedCallback.EVENT.invoker().onClaimDeleted(claim, player);
        return claim;
    }

    synchronized @Nullable ClaimSnapshot setTrustAt(
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @NotNull String identifier,
            @NotNull ClaimTrustLevel levelToGrant)
            throws IOException
    {
        if (levelToGrant == ClaimTrustLevel.EDIT)
        {
            throw new IllegalArgumentException("Edit trust is owner-only.");
        }

        ClaimSnapshot claim = findClaimAt(level, pos);
        if (claim == null || claim.id() == null)
        {
            return null;
        }

        String normalized = requireIdentifier(identifier);
        ClaimTrustSnapshot existingTrust = trustForOrEmpty(claim);
        Map<String, ClaimTrustLevel> permissions = new LinkedHashMap<>(existingTrust.permissionsByIdentifier());
        Set<String> managers = new LinkedHashSet<>(existingTrust.managerIdentifiers());
        Set<String> denies = new LinkedHashSet<>(existingTrust.deniedIdentifiers());

        if (levelToGrant == ClaimTrustLevel.MANAGE)
        {
            permissions.remove(normalized);
            managers.add(normalized);
        }
        else
        {
            managers.remove(normalized);
            permissions.put(normalized, levelToGrant);
        }
        removeDenyEntries(denies, normalized);

        Map<Long, ClaimTrustSnapshot> trust = mutableTrust();
        trust.put(claim.id(), new ClaimTrustSnapshot(claim.ownerId(), permissions, managers, denies));
        replaceAndSave(mutableSnapshots(), trust);
        return claim;
    }

    synchronized @Nullable ClaimSnapshot removeTrustAt(
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @NotNull String identifier)
            throws IOException
    {
        ClaimSnapshot claim = findClaimAt(level, pos);
        if (claim == null || claim.id() == null)
        {
            return null;
        }

        String normalized = requireIdentifier(identifier);
        ClaimTrustSnapshot existingTrust = trustForOrEmpty(claim);
        Map<String, ClaimTrustLevel> permissions = new LinkedHashMap<>(existingTrust.permissionsByIdentifier());
        Set<String> managers = new LinkedHashSet<>(existingTrust.managerIdentifiers());
        Set<String> denies = new LinkedHashSet<>(existingTrust.deniedIdentifiers());
        permissions.remove(normalized);
        managers.remove(normalized);
        removeDenyEntries(denies, normalized);

        Map<Long, ClaimTrustSnapshot> trust = mutableTrust();
        trust.put(claim.id(), new ClaimTrustSnapshot(claim.ownerId(), permissions, managers, denies));
        replaceAndSave(mutableSnapshots(), trust);
        return claim;
    }

    synchronized @Nullable ClaimSnapshot findClaimAt(@NotNull ServerLevel level, @NotNull BlockPos pos)
    {
        return this.claimIndex.findAt(worldKey(level), pos.getX(), pos.getY(), pos.getZ(), false, false);
    }

    synchronized @Nullable ClaimTrustSnapshot trustFor(@NotNull ClaimSnapshot claim)
    {
        Long id = claim.id();
        return id == null ? null : this.trustByClaimId.get(id);
    }

    @NotNull String worldKey(@NotNull ServerLevel level)
    {
        String identifier = level.dimension().identifier().toString();
        if ("minecraft:overworld".equals(identifier))
        {
            return "world";
        }
        if ("minecraft:the_nether".equals(identifier))
        {
            return "world_nether";
        }
        if ("minecraft:the_end".equals(identifier))
        {
            return "world_the_end";
        }
        return identifier;
    }

    private void replaceAndSave(
            @NotNull List<ClaimSnapshot> snapshots,
            @NotNull Map<Long, ClaimTrustSnapshot> trust)
            throws IOException
    {
        FabricClaimFileStore.save(this.dataFolder, snapshots, trust, this.nextClaimId);
        this.claimIndex.rebuild(snapshots);
        this.trustByClaimId.clear();
        this.trustByClaimId.putAll(trust);
        this.logger.info("Saved {} native Fabric claims to {}.", snapshots.size(), this.dataFolder);
    }

    private @NotNull List<ClaimSnapshot> mutableSnapshots()
    {
        return new ArrayList<>(this.claimIndex.snapshots());
    }

    private @NotNull Map<Long, ClaimTrustSnapshot> mutableTrust()
    {
        return new LinkedHashMap<>(this.trustByClaimId);
    }

    private @NotNull ClaimTrustSnapshot trustForOrEmpty(@NotNull ClaimSnapshot claim)
    {
        ClaimTrustSnapshot trust = trustFor(claim);
        return trust == null ? ClaimTrustSnapshot.empty(claim.ownerId()) : trust;
    }

    private static @NotNull String requireIdentifier(@NotNull String identifier)
    {
        String normalized = ClaimTrustSnapshot.normalizeIdentifier(identifier);
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException("Identifier cannot be blank.");
        }
        return normalized;
    }

    private static void removeDenyEntries(@NotNull Set<String> denies, @NotNull String normalizedIdentifier)
    {
        denies.remove(normalizedIdentifier);
        denies.remove(normalizedIdentifier + ClaimTrustLevel.MANAGE.denySuffix());
        denies.remove(normalizedIdentifier + ClaimTrustLevel.BUILD.denySuffix());
        denies.remove(normalizedIdentifier + ClaimTrustLevel.CONTAINER.denySuffix());
        denies.remove(normalizedIdentifier + ClaimTrustLevel.ACCESS.denySuffix());
    }

    // ClaimRepository interface methods

    @Override
    public @NotNull Collection<ClaimSnapshot> getClaims()
    {
        return this.claimIndex.snapshots();
    }

    @Override
    public @NotNull Collection<ClaimSnapshot> getClaims(@NotNull UUID owner)
    {
        List<ClaimSnapshot> result = new ArrayList<>();
        for (ClaimSnapshot snapshot : this.claimIndex.snapshots())
        {
            if (owner.equals(snapshot.ownerId()))
            {
                result.add(snapshot);
            }
        }
        return result;
    }

    @Override
    public @NotNull Optional<ClaimSnapshot> getClaim(long id)
    {
        return Optional.ofNullable(this.claimIndex.get(id));
    }

    @Override
    public @NotNull Optional<ClaimSnapshot> findClaimAt(
            @NotNull String worldKey,
            int x, int y, int z,
            boolean ignoreHeight,
            boolean ignoreSubclaims)
    {
        ClaimSnapshot result = this.claimIndex.findAt(worldKey, x, y, z, ignoreHeight, ignoreSubclaims);
        return Optional.ofNullable(result);
    }

    @Override
    public @NotNull Collection<ClaimSnapshot> candidates(@NotNull String worldKey, @NotNull ClaimBounds bounds)
    {
        return this.claimIndex.candidates(worldKey, bounds);
    }

    static final class CreateClaimResult
    {
        private final @Nullable ClaimSnapshot created;
        private final @Nullable ClaimSnapshot overlapping;

        private CreateClaimResult(@Nullable ClaimSnapshot created, @Nullable ClaimSnapshot overlapping)
        {
            this.created = created;
            this.overlapping = overlapping;
        }

        static @NotNull CreateClaimResult created(@NotNull ClaimSnapshot claim)
        {
            return new CreateClaimResult(claim, null);
        }

        static @NotNull CreateClaimResult overlap(@NotNull ClaimSnapshot claim)
        {
            return new CreateClaimResult(null, claim);
        }

        boolean created()
        {
            return this.created != null;
        }

        @Nullable ClaimSnapshot createdClaim()
        {
            return this.created;
        }

        @Nullable ClaimSnapshot overlappingClaim()
        {
            return this.overlapping;
        }
    }

    static final class UpdateClaimResult
    {
        private final @Nullable ClaimSnapshot updated;
        private final @Nullable ClaimSnapshot overlapping;
        private final boolean missing;

        private UpdateClaimResult(
                @Nullable ClaimSnapshot updated,
                @Nullable ClaimSnapshot overlapping,
                boolean missing)
        {
            this.updated = updated;
            this.overlapping = overlapping;
            this.missing = missing;
        }

        static @NotNull UpdateClaimResult updated(@NotNull ClaimSnapshot claim)
        {
            return new UpdateClaimResult(claim, null, false);
        }

        static @NotNull UpdateClaimResult overlap(@NotNull ClaimSnapshot claim)
        {
            return new UpdateClaimResult(null, claim, false);
        }

        static @NotNull UpdateClaimResult missingResult()
        {
            return new UpdateClaimResult(null, null, true);
        }

        boolean updated()
        {
            return this.updated != null;
        }

        boolean isMissing()
        {
            return this.missing;
        }

        @Nullable ClaimSnapshot updatedClaim()
        {
            return this.updated;
        }

        @Nullable ClaimSnapshot overlappingClaim()
        {
            return this.overlapping;
        }
    }
}
