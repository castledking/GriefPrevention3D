package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimRepository;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimSnapshotIndex;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.persistence.ClaimDocument;
import net.minecraft.commands.Commands;
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
    private static final String OVERRIDE_CLAIM_COUNT_PERMISSION =
            "griefprevention.overrideclaimcountlimit";
    static final String CLAIM_COUNT_LIMIT_MESSAGE =
            "You've reached your limit on land claims. Use /abandonclaim to remove one before creating another.";

    private final ClaimSnapshotIndex claimIndex = new ClaimSnapshotIndex();
    private final Map<Long, ClaimDocument> documentsByClaimId = new LinkedHashMap<>();
    private final Path dataFolder;
    private final Logger logger;
    private final FabricPermissionResolver permissions;
    private final FabricClaimBlockService claimBlocks;
    private long nextClaimId;

    FabricClaimRepository(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        this(dataFolder, logger, FabricPermissions.detect(logger));
    }

    FabricClaimRepository(
            @NotNull Path dataFolder,
            @NotNull Logger logger,
            @NotNull FabricPermissionResolver permissions)
    {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.permissions = permissions;
        this.claimBlocks = new FabricClaimBlockService(dataFolder, logger, permissions);
        reload();
    }

    synchronized int reload()
    {
        this.claimBlocks.reload();
        FabricClaimFileStore.LoadedClaims loaded = FabricClaimFileStore.load(this.dataFolder, logger);
        this.claimIndex.rebuild(loaded.snapshots());
        this.documentsByClaimId.clear();
        for (ClaimDocument document : loaded.documents())
        {
            this.documentsByClaimId.put(document.snapshot().id(), document);
        }
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

    @NotNull FabricClaimBlockService claimBlockService()
    {
        return this.claimBlocks;
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
        return createClaim(
                worldKey(level),
                bounds,
                ownerId,
                player,
                bypassesClaimCountLimit(ownerId, player)
        );
    }

    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull String worldKey,
            @NotNull ClaimBounds bounds,
            @NotNull UUID ownerId,
            @Nullable ServerPlayer player)
            throws IOException
    {
        return createClaim(worldKey, bounds, ownerId, player, bypassesClaimCountLimit(ownerId, player));
    }

    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull String worldKey,
            @NotNull ClaimBounds bounds,
            @NotNull UUID ownerId,
            @Nullable ServerPlayer player,
            boolean bypassClaimCountLimit)
            throws IOException
    {
        if (!bypassClaimCountLimit && hasReachedClaimCountLimit(ownerId))
        {
            return CreateClaimResult.claimCountLimitReached();
        }

        ClaimSnapshot snapshot = new ClaimSnapshot(
                this.nextClaimId,
                worldKey,
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

        ClaimBlockBalance balance = this.claimBlocks.balance(ownerId, this.claimIndex.snapshots());
        int claimArea = snapshot.bounds().area();
        if (claimArea > balance.remaining())
        {
            return CreateClaimResult.insufficientClaimBlocks(
                    missingBlocks(claimArea, balance.remaining()),
                    balance.remaining()
            );
        }

        List<ClaimDocument> documents = mutableDocuments();
        documents.add(ClaimDocument.create(snapshot, System.currentTimeMillis()));
        long previousNextClaimId = this.nextClaimId;
        this.nextClaimId = Math.max(this.nextClaimId + 1L, snapshot.id() + 1L);
        try
        {
            replaceAndSave(documents);
        }
        catch (IOException e)
        {
            this.nextClaimId = previousNextClaimId;
            throw e;
        }
        ClaimCreatedCallback.EVENT.invoker().onClaimCreated(snapshot, player);
        return CreateClaimResult.created(snapshot, balance.remaining() - claimArea);
    }

    synchronized boolean hasReachedClaimCountLimit(@NotNull ServerPlayer player)
    {
        UUID playerId = player.getUUID();
        return !bypassesClaimCountLimit(playerId, player)
                && hasReachedClaimCountLimit(playerId);
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

        ClaimDocument existingDocument = this.documentsByClaimId.get(claimId);
        if (existingDocument == null)
        {
            return UpdateClaimResult.missingResult();
        }

        Integer remainingAfter = null;
        if (existing.ownerId() != null
                && existing.parentId() == null
                && !existing.subdivision())
        {
            ClaimBlockBalance balance = this.claimBlocks.balance(
                    existing.ownerId(),
                    this.claimIndex.snapshots()
            );
            // Preserve the established Bukkit resize calculation: refund the old top-level area,
            // then charge the replacement area against the derived remaining balance.
            remainingAfter = balance.remaining() + (existing.bounds().area() - updated.bounds().area());
            if (remainingAfter < 0)
            {
                return UpdateClaimResult.insufficientClaimBlocks(
                        negativeMagnitude(remainingAfter),
                        balance.remaining()
                );
            }
        }

        List<ClaimDocument> documents = mutableDocuments();
        replaceDocument(
                documents,
                existingDocument.withSnapshot(updated, System.currentTimeMillis())
        );
        replaceAndSave(documents);
        ClaimModifiedCallback.EVENT.invoker().onClaimModified(existing, updated, player);
        return UpdateClaimResult.updated(updated, remainingAfter);
    }

    synchronized @Nullable ClaimSnapshot deleteClaimAt(@NotNull ServerLevel level, @NotNull BlockPos pos, @Nullable ServerPlayer player)
            throws IOException
    {
        ClaimSnapshot claim = findClaimAt(level, pos);
        if (claim == null || claim.id() == null)
        {
            return null;
        }

        return deleteClaim(claim.id(), player);
    }

    synchronized @Nullable ClaimSnapshot deleteClaim(long claimId, @Nullable ServerPlayer player)
            throws IOException
    {
        return deleteClaimAs(claimId, player == null ? null : player.getUUID(), player);
    }

    synchronized @Nullable ClaimSnapshot deleteClaimAs(
            long claimId,
            @Nullable UUID actorId,
            @Nullable ServerPlayer player)
            throws IOException
    {
        ClaimSnapshot claim = this.claimIndex.get(claimId);
        if (claim == null)
        {
            return null;
        }

        FabricClaimBlockService.PlayerDataUpdate playerDataUpdate = null;
        if (actorId != null
                && actorId.equals(claim.ownerId())
                && claim.parentId() == null
                && !claim.subdivision())
        {
            playerDataUpdate = this.claimBlocks.prepareAbandonment(
                    actorId,
                    claim.bounds().area()
            );
        }

        List<ClaimDocument> originalDocuments = mutableDocuments();
        List<ClaimDocument> updatedDocuments = new ArrayList<>(originalDocuments);
        Set<Long> deletedIds = descendantIds(claimId, updatedDocuments);
        updatedDocuments.removeIf(document -> deletedIds.contains(document.snapshot().id()));
        replaceAndSave(updatedDocuments);
        if (playerDataUpdate != null)
        {
            try
            {
                this.claimBlocks.apply(playerDataUpdate);
            }
            catch (IOException playerDataFailure)
            {
                try
                {
                    replaceAndSave(originalDocuments);
                }
                catch (IOException rollbackFailure)
                {
                    playerDataFailure.addSuppressed(rollbackFailure);
                }
                throw playerDataFailure;
            }
        }
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
        Set<String> neighbors = new LinkedHashSet<>(existingTrust.neighborIdentifiers());
        Set<String> denies = new LinkedHashSet<>(existingTrust.deniedIdentifiers());

        // Interaction, manage, and neighbor trust are independent tracks.
        if (levelToGrant == ClaimTrustLevel.MANAGE)
        {
            managers.add(normalized);
        }
        else if (levelToGrant == ClaimTrustLevel.NEIGHBOR)
        {
            neighbors.add(normalized);
        }
        else
        {
            permissions.put(normalized, levelToGrant);
        }
        removeDenyEntries(denies, normalized);

        ClaimDocument document = this.documentsByClaimId.get(claim.id());
        if (document == null)
        {
            return null;
        }
        List<ClaimDocument> documents = mutableDocuments();
        replaceDocument(documents, document.withTrust(
                new ClaimTrustSnapshot(claim.ownerId(), permissions, managers, neighbors, denies)
        ));
        replaceAndSave(documents);
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
        Set<String> neighbors = new LinkedHashSet<>(existingTrust.neighborIdentifiers());
        Set<String> denies = new LinkedHashSet<>(existingTrust.deniedIdentifiers());
        permissions.remove(normalized);
        managers.remove(normalized);
        neighbors.remove(normalized);
        removeDenyEntries(denies, normalized);

        ClaimDocument document = this.documentsByClaimId.get(claim.id());
        if (document == null)
        {
            return null;
        }
        List<ClaimDocument> documents = mutableDocuments();
        replaceDocument(documents, document.withTrust(
                new ClaimTrustSnapshot(claim.ownerId(), permissions, managers, neighbors, denies)
        ));
        replaceAndSave(documents);
        return claim;
    }

    synchronized @Nullable ClaimSnapshot findClaimAt(@NotNull ServerLevel level, @NotNull BlockPos pos)
    {
        return this.claimIndex.findAt(worldKey(level), pos.getX(), pos.getY(), pos.getZ(), false, false);
    }

    synchronized @Nullable ClaimTrustSnapshot trustFor(@NotNull ClaimSnapshot claim)
    {
        Long id = claim.id();
        ClaimDocument document = id == null ? null : this.documentsByClaimId.get(id);
        return document == null ? null : document.trust();
    }

    synchronized boolean allows(
            @NotNull ClaimSnapshot claim,
            @NotNull UUID playerId,
            @NotNull ClaimTrustLevel required)
    {
        ClaimTrustSnapshot trust = trustForOrEmpty(claim);
        return FabricClaimTrustEvaluator.allows(playerId, trust, required, this.permissions);
    }

    synchronized boolean canGrantManageTrust(@NotNull ServerPlayer player)
    {
        return FabricTrustCommandAuthorization.canGrantManageTrust(
                player.getUUID(),
                this.permissions
        );
    }

    synchronized boolean canGrantPermissionTrust(@NotNull ServerPlayer player)
    {
        boolean operatorDefault = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        return FabricTrustCommandAuthorization.canGrantPermissionTrust(
                player.getUUID(),
                this.permissions,
                operatorDefault
        );
    }

    synchronized @Nullable ClaimDocument documentFor(long claimId)
    {
        return this.documentsByClaimId.get(claimId);
    }

    synchronized @NotNull ClaimBlockBalance claimBlockBalance(@NotNull UUID playerId) throws IOException
    {
        return this.claimBlocks.balance(playerId, this.claimIndex.snapshots());
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
            @NotNull List<ClaimDocument> documents)
            throws IOException
    {
        FabricClaimFileStore.save(this.dataFolder, documents, this.nextClaimId);
        List<ClaimSnapshot> snapshots = new ArrayList<>();
        Map<Long, ClaimDocument> byId = new LinkedHashMap<>();
        for (ClaimDocument document : documents)
        {
            snapshots.add(document.snapshot());
            byId.put(document.snapshot().id(), document);
        }
        this.claimIndex.rebuild(snapshots);
        this.documentsByClaimId.clear();
        this.documentsByClaimId.putAll(byId);
        this.logger.info("Saved {} native Fabric claims to {}.", snapshots.size(), this.dataFolder);
    }

    private @NotNull List<ClaimDocument> mutableDocuments()
    {
        return new ArrayList<>(this.documentsByClaimId.values());
    }

    private static void replaceDocument(
            @NotNull List<ClaimDocument> documents,
            @NotNull ClaimDocument updated)
    {
        Long updatedId = updated.snapshot().id();
        for (int i = 0; i < documents.size(); i++)
        {
            if (updatedId.equals(documents.get(i).snapshot().id()))
            {
                documents.set(i, updated);
                return;
            }
        }
        throw new IllegalStateException("Missing claim document " + updatedId + ".");
    }

    private static @NotNull Set<Long> descendantIds(
            @NotNull Long rootId,
            @NotNull Collection<ClaimDocument> documents)
    {
        Set<Long> result = new LinkedHashSet<>();
        result.add(rootId);
        boolean changed;
        do
        {
            changed = false;
            for (ClaimDocument document : documents)
            {
                Long parentId = document.snapshot().parentId();
                Long id = document.snapshot().id();
                if (parentId != null && result.contains(parentId) && result.add(id))
                {
                    changed = true;
                }
            }
        }
        while (changed);
        return result;
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
        denies.remove(normalizedIdentifier + ClaimTrustLevel.NEIGHBOR.denySuffix());
    }

    private static int missingBlocks(int required, int remaining)
    {
        long missing = (long) required - (long) remaining;
        return missing > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, missing);
    }

    private static int negativeMagnitude(int value)
    {
        long magnitude = -(long) value;
        return magnitude > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) magnitude;
    }

    private boolean hasReachedClaimCountLimit(@NotNull UUID ownerId)
    {
        int maximumClaims = this.claimBlocks.maximumClaimsPerPlayer();
        if (maximumClaims <= 0)
        {
            return false;
        }

        int ownedTopLevelClaims = 0;
        for (ClaimSnapshot snapshot : this.claimIndex.snapshots())
        {
            if (ownerId.equals(snapshot.ownerId())
                    && snapshot.parentId() == null
                    && !snapshot.subdivision()
                    && ++ownedTopLevelClaims >= maximumClaims)
            {
                return true;
            }
        }
        return false;
    }

    private boolean bypassesClaimCountLimit(
            @NotNull UUID ownerId,
            @Nullable ServerPlayer player)
    {
        if (player == null || !ownerId.equals(player.getUUID()))
        {
            return false;
        }
        boolean operatorDefault = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        return this.claimBlocks.permissionOrDefault(
                ownerId,
                OVERRIDE_CLAIM_COUNT_PERMISSION,
                operatorDefault
        );
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
        private final boolean claimCountLimitReached;
        private final boolean insufficientClaimBlocks;
        private final int blocksNeeded;
        private final @Nullable Integer remainingBlocks;

        private CreateClaimResult(
                @Nullable ClaimSnapshot created,
                @Nullable ClaimSnapshot overlapping,
                boolean claimCountLimitReached,
                boolean insufficientClaimBlocks,
                int blocksNeeded,
                @Nullable Integer remainingBlocks)
        {
            this.created = created;
            this.overlapping = overlapping;
            this.claimCountLimitReached = claimCountLimitReached;
            this.insufficientClaimBlocks = insufficientClaimBlocks;
            this.blocksNeeded = blocksNeeded;
            this.remainingBlocks = remainingBlocks;
        }

        static @NotNull CreateClaimResult created(@NotNull ClaimSnapshot claim, int remainingBlocks)
        {
            return new CreateClaimResult(claim, null, false, false, 0, remainingBlocks);
        }

        static @NotNull CreateClaimResult overlap(@NotNull ClaimSnapshot claim)
        {
            return new CreateClaimResult(null, claim, false, false, 0, null);
        }

        static @NotNull CreateClaimResult claimCountLimitReached()
        {
            return new CreateClaimResult(null, null, true, false, 0, null);
        }

        static @NotNull CreateClaimResult insufficientClaimBlocks(int blocksNeeded, int remainingBlocks)
        {
            return new CreateClaimResult(null, null, false, true, blocksNeeded, remainingBlocks);
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

        boolean hasReachedClaimCountLimit()
        {
            return this.claimCountLimitReached;
        }

        boolean hasInsufficientClaimBlocks()
        {
            return this.insufficientClaimBlocks;
        }

        int blocksNeeded()
        {
            return this.blocksNeeded;
        }

        @Nullable Integer remainingBlocks()
        {
            return this.remainingBlocks;
        }
    }

    static final class UpdateClaimResult
    {
        private final @Nullable ClaimSnapshot updated;
        private final @Nullable ClaimSnapshot overlapping;
        private final boolean missing;
        private final boolean insufficientClaimBlocks;
        private final int blocksNeeded;
        private final @Nullable Integer remainingBlocks;

        private UpdateClaimResult(
                @Nullable ClaimSnapshot updated,
                @Nullable ClaimSnapshot overlapping,
                boolean missing,
                boolean insufficientClaimBlocks,
                int blocksNeeded,
                @Nullable Integer remainingBlocks)
        {
            this.updated = updated;
            this.overlapping = overlapping;
            this.missing = missing;
            this.insufficientClaimBlocks = insufficientClaimBlocks;
            this.blocksNeeded = blocksNeeded;
            this.remainingBlocks = remainingBlocks;
        }

        static @NotNull UpdateClaimResult updated(
                @NotNull ClaimSnapshot claim,
                @Nullable Integer remainingBlocks)
        {
            return new UpdateClaimResult(claim, null, false, false, 0, remainingBlocks);
        }

        static @NotNull UpdateClaimResult overlap(@NotNull ClaimSnapshot claim)
        {
            return new UpdateClaimResult(null, claim, false, false, 0, null);
        }

        static @NotNull UpdateClaimResult missingResult()
        {
            return new UpdateClaimResult(null, null, true, false, 0, null);
        }

        static @NotNull UpdateClaimResult insufficientClaimBlocks(int blocksNeeded, int remainingBlocks)
        {
            return new UpdateClaimResult(
                    null,
                    null,
                    false,
                    true,
                    blocksNeeded,
                    remainingBlocks
            );
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

        boolean hasInsufficientClaimBlocks()
        {
            return this.insufficientClaimBlocks;
        }

        int blocksNeeded()
        {
            return this.blocksNeeded;
        }

        @Nullable Integer remainingBlocks()
        {
            return this.remainingBlocks;
        }
    }
}
