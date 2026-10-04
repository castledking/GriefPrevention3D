package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimFlag;
import com.griefprevention.claims.ClaimOwnership;
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
import net.minecraft.world.entity.player.Player;
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
import java.util.concurrent.ConcurrentHashMap;

public final class FabricClaimRepository implements ClaimRepository
{
    private static final String OVERRIDE_CLAIM_COUNT_PERMISSION =
            "griefprevention.overrideclaimcountlimit";
    /** The field Paper writes on a subdivision staff administer. */
    private static final String ADMIN_SUBDIVISION_FIELD = "Admin Subdivision";
    private final ClaimSnapshotIndex claimIndex = new ClaimSnapshotIndex();
    private final Map<Long, ClaimDocument> documentsByClaimId = new LinkedHashMap<>();
    private final Path dataFolder;
    private final Logger logger;
    private final FabricPermissionResolver permissions;
    private final FabricClaimBlockService claimBlocks;
    /** Players in {@code /ignoreclaims} mode; like Paper's, it lasts until they log out. */
    private final Set<UUID> ignoringClaims = ConcurrentHashMap.newKeySet();
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

    /**
     * @param ownerId the new claim's owner, or null for an administrative claim
     */
    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull ServerLevel level,
            @NotNull BlockPos center,
            @Nullable UUID ownerId,
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

    /**
     * @param ownerId the new claim's owner, or null for an administrative claim
     */
    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull ServerLevel level,
            @NotNull BlockPos firstCorner,
            @NotNull BlockPos secondCorner,
            @Nullable UUID ownerId,
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
                ownerId == null || bypassesClaimCountLimit(ownerId, player)
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

    /**
     * Creates a top-level claim. An administrative claim ({@code ownerId} null) is free and counts
     * toward nobody's claim limit, as on Paper.
     */
    synchronized @NotNull CreateClaimResult createClaim(
            @NotNull String worldKey,
            @NotNull ClaimBounds bounds,
            @Nullable UUID ownerId,
            @Nullable ServerPlayer player,
            boolean bypassClaimCountLimit)
            throws IOException
    {
        if (ownerId != null && !bypassClaimCountLimit && hasReachedClaimCountLimit(ownerId))
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

        Integer remainingAfter = null;
        if (ownerId != null)
        {
            ClaimBlockBalance balance = this.claimBlocks.balance(ownerId, this.claimIndex.snapshots());
            int claimArea = snapshot.bounds().area();
            if (claimArea > balance.remaining())
            {
                return CreateClaimResult.insufficientClaimBlocks(
                        missingBlocks(claimArea, balance.remaining()),
                        balance.remaining()
                );
            }
            remainingAfter = balance.remaining() - claimArea;
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
        return CreateClaimResult.created(snapshot, remainingAfter);
    }

    /**
     * Whether a player may not take on another top-level claim, online or not. Paper's
     * {@code overrideclaimcountlimit} permission is only read while the player is online.
     */
    synchronized boolean isAtClaimCountLimit(@NotNull UUID playerId, @Nullable ServerPlayer online)
    {
        return !bypassesClaimCountLimit(playerId, online) && hasReachedClaimCountLimit(playerId);
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

    /**
     * Gives a top-level claim a new owner, keeping its trust, flags and shape, as Paper's
     * {@code changeClaimOwner}. Subdivisions that recorded the old owner follow the claim.
     *
     * @param newOwnerId the new owner, or null to make the claim administrative
     * @return the claim under its new owner, or null when the claim is unknown
     * @throws IllegalArgumentException for a subdivision, which only changes hands with its claim
     */
    synchronized @Nullable ClaimSnapshot changeOwner(
            long claimId,
            @Nullable UUID newOwnerId,
            @Nullable ServerPlayer actor)
            throws IOException
    {
        ClaimDocument document = this.documentsByClaimId.get(claimId);
        if (document == null)
        {
            return null;
        }
        if (document.snapshot().parentId() != null)
        {
            throw new IllegalArgumentException("Only top-level claims change owner.");
        }

        UUID previousOwnerId = document.snapshot().ownerId();
        long now = System.currentTimeMillis();
        List<ClaimDocument> documents = mutableDocuments();
        for (Long id : descendantIds(claimId, documents))
        {
            ClaimDocument member = this.documentsByClaimId.get(id);
            if (id == claimId || (previousOwnerId != null && previousOwnerId.equals(member.snapshot().ownerId())))
            {
                replaceDocument(documents, member.withOwner(newOwnerId, now));
            }
        }
        replaceAndSave(documents);
        ClaimSnapshot updated = this.claimIndex.get(claimId);
        ClaimTransferredCallback.EVENT.invoker().onClaimTransferred(updated, newOwnerId, actor);
        return updated;
    }

    /** @return the administrative claims, not counting subdivisions */
    synchronized @NotNull List<ClaimSnapshot> topLevelAdminClaims()
    {
        List<ClaimSnapshot> result = new ArrayList<>();
        for (ClaimSnapshot claim : this.claimIndex.snapshots())
        {
            if (claim.parentId() == null && claim.ownerId() == null)
            {
                result.add(claim);
            }
        }
        return result;
    }

    /**
     * Grants trust in each claim and in the subdivisions that inherit from it, as Paper's trust
     * commands do, saving once.
     */
    synchronized void grantTrust(
            @NotNull Collection<ClaimSnapshot> targets,
            @NotNull String identifier,
            @NotNull ClaimTrustLevel level)
            throws IOException
    {
        if (level == ClaimTrustLevel.EDIT)
        {
            throw new IllegalArgumentException("Edit trust is owner-only.");
        }
        String normalized = requireIdentifier(identifier);
        updateTrust(inheritingClaimIds(targets), trust -> {
            Map<String, ClaimTrustLevel> permissions = new LinkedHashMap<>(trust.permissionsByIdentifier());
            Set<String> managers = new LinkedHashSet<>(trust.managerIdentifiers());
            Set<String> neighbors = new LinkedHashSet<>(trust.neighborIdentifiers());
            // Manage, neighbor and interaction trust are independent tracks, as in Paper's setPermission.
            if (level == ClaimTrustLevel.MANAGE)
            {
                managers.add(normalized);
            }
            else if (level == ClaimTrustLevel.NEIGHBOR)
            {
                neighbors.add(normalized);
            }
            else
            {
                permissions.put(normalized, level);
            }
            return new ClaimTrustSnapshot(trust.ownerId(), permissions, managers, neighbors,
                    trust.deniedIdentifiers(), trust.pvpTrustedIdentifiers(), trust.pveTrustedIdentifiers());
        });
    }

    /**
     * Revokes every kind of trust an identifier holds in each claim and all its subdivisions, as
     * Paper's {@code dropPermission} does. Deny entries stay, so a subdivision keeps refusing trust
     * its parent might grant again.
     */
    synchronized void revokeTrust(
            @NotNull Collection<ClaimSnapshot> targets,
            @NotNull String identifier)
            throws IOException
    {
        String normalized = requireIdentifier(identifier);
        updateTrust(descendantIds(targets), trust -> {
            Map<String, ClaimTrustLevel> permissions = new LinkedHashMap<>(trust.permissionsByIdentifier());
            Set<String> managers = new LinkedHashSet<>(trust.managerIdentifiers());
            Set<String> neighbors = new LinkedHashSet<>(trust.neighborIdentifiers());
            Set<String> pvp = new LinkedHashSet<>(trust.pvpTrustedIdentifiers());
            Set<String> pve = new LinkedHashSet<>(trust.pveTrustedIdentifiers());
            permissions.remove(normalized);
            managers.remove(normalized);
            neighbors.remove(normalized);
            pvp.remove(normalized);
            pve.remove(normalized);
            return new ClaimTrustSnapshot(trust.ownerId(), permissions, managers, neighbors,
                    trust.deniedIdentifiers(), pvp, pve);
        });
    }

    /** Clears all trust, including deny entries, in each claim and all its subdivisions. */
    synchronized void clearTrust(@NotNull Collection<ClaimSnapshot> targets) throws IOException
    {
        updateTrust(descendantIds(targets), trust -> ClaimTrustSnapshot.empty(trust.ownerId()));
    }

    private void updateTrust(
            @NotNull Set<Long> claimIds,
            @NotNull java.util.function.UnaryOperator<ClaimTrustSnapshot> change)
            throws IOException
    {
        List<ClaimDocument> documents = mutableDocuments();
        boolean changed = false;
        for (Long claimId : claimIds)
        {
            ClaimDocument document = this.documentsByClaimId.get(claimId);
            if (document == null)
            {
                continue;
            }
            ClaimTrustSnapshot updated = change.apply(document.trust());
            if (!updated.equals(document.trust()))
            {
                replaceDocument(documents, document.withTrust(updated));
                changed = true;
            }
        }
        if (changed)
        {
            replaceAndSave(documents);
        }
    }

    /**
     * @return the claims plus the subdivisions below them that inherit their trust: not restricted,
     *         and not staff-administered, as Paper's {@code propagateTrustToChildren} walks them
     */
    private @NotNull Set<Long> inheritingClaimIds(@NotNull Collection<ClaimSnapshot> targets)
    {
        Set<Long> result = new LinkedHashSet<>();
        for (ClaimSnapshot target : targets)
        {
            if (target.id() != null)
            {
                result.add(target.id());
            }
        }
        boolean changed;
        do
        {
            changed = false;
            for (ClaimDocument document : this.documentsByClaimId.values())
            {
                Long parentId = document.snapshot().parentId();
                if (parentId != null
                        && result.contains(parentId)
                        && !document.inheritNothing()
                        && !isAdminSubdivision(document)
                        && result.add(document.snapshot().id()))
                {
                    changed = true;
                }
            }
        }
        while (changed);
        return result;
    }

    private @NotNull Set<Long> descendantIds(@NotNull Collection<ClaimSnapshot> targets)
    {
        Set<Long> result = new LinkedHashSet<>();
        for (ClaimSnapshot target : targets)
        {
            if (target.id() != null)
            {
                result.addAll(descendantIds(target.id(), this.documentsByClaimId.values()));
            }
        }
        return result;
    }

    private static boolean isAdminSubdivision(@NotNull ClaimDocument document)
    {
        return Boolean.TRUE.equals(document.extraFields().get(ADMIN_SUBDIVISION_FIELD));
    }

    /** @return the top-level claims a player owns, which Paper's all-claims commands act on */
    synchronized @NotNull List<ClaimSnapshot> topLevelClaimsOwnedBy(@NotNull UUID ownerId)
    {
        List<ClaimSnapshot> result = new ArrayList<>();
        for (ClaimSnapshot snapshot : this.claimIndex.snapshots())
        {
            if (ownerId.equals(snapshot.ownerId()) && snapshot.parentId() == null && !snapshot.subdivision())
            {
                result.add(snapshot);
            }
        }
        return result;
    }

    synchronized boolean hasSubdivisions(@NotNull ClaimSnapshot claim)
    {
        Long id = claim.id();
        if (id == null)
        {
            return false;
        }
        for (ClaimSnapshot snapshot : this.claimIndex.snapshots())
        {
            if (id.equals(snapshot.parentId()))
            {
                return true;
            }
        }
        return false;
    }

    synchronized @Nullable ClaimSnapshot findClaimAt(@NotNull ServerLevel level, @NotNull BlockPos pos)
    {
        return this.claimIndex.findAt(worldKey(level), pos.getX(), pos.getY(), pos.getZ(), false, false);
    }

    synchronized @Nullable ClaimSnapshot claimById(long claimId)
    {
        return this.claimIndex.get(claimId);
    }

    /**
     * @return the flag's current value, or null when the claim is unknown
     */
    synchronized @Nullable Boolean flag(long claimId, @NotNull ClaimFlag flag)
    {
        ClaimDocument document = this.documentsByClaimId.get(claimId);
        return document == null ? null : document.flag(flag);
    }

    /**
     * Writes one policy flag and persists the claim.
     *
     * @return the stored value, or null when the claim is unknown
     */
    synchronized @Nullable Boolean setFlag(long claimId, @NotNull ClaimFlag flag, boolean value)
            throws IOException
    {
        ClaimDocument document = this.documentsByClaimId.get(claimId);
        if (document == null)
        {
            return null;
        }
        if (document.flag(flag) == value)
        {
            // Nothing to write; avoid rewriting the file for a no-op toggle.
            return value;
        }

        List<ClaimDocument> documents = mutableDocuments();
        replaceDocument(documents, document.withFlag(flag, value));
        replaceAndSave(documents);
        return value;
    }

    /**
     * Records an explicit /claimpvp toggle, including the marker Paper reads to know the claim's own
     * setting decides.
     *
     * @return false when the claim is unknown
     */
    synchronized boolean setPvpToggle(long claimId, boolean enabled) throws IOException
    {
        ClaimDocument document = this.documentsByClaimId.get(claimId);
        if (document == null)
        {
            return false;
        }
        List<ClaimDocument> documents = mutableDocuments();
        replaceDocument(documents, document.withPvpToggle(enabled));
        replaceAndSave(documents);
        return true;
    }

    /**
     * @return whether the claim is a subdivision that inherits nothing from its parent
     */
    synchronized boolean isRestrictedSubdivision(@NotNull ClaimSnapshot claim)
    {
        Long id = claim.id();
        ClaimDocument document = id == null ? null : this.documentsByClaimId.get(id);
        return claim.parentId() != null && document != null && document.inheritNothing();
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

    /**
     * Checks trust the way Paper's {@code Claim.checkPermission} does for a player: staff with
     * {@code griefprevention.adminclaims} hold every permission in administrative claims, and
     * everyone else needs to own the claim or be trusted in it.
     */
    boolean allows(
            @NotNull ClaimSnapshot claim,
            @NotNull Player player,
            @NotNull ClaimTrustLevel required)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            if (isAdminClaim(claim))
            {
                if (hasPermission(serverPlayer, FabricPermissionDefaults.ADMIN_CLAIMS))
                {
                    return true;
                }
            }
            // Anyone with deleteclaims may edit another player's claim at any time.
            else if (required == ClaimTrustLevel.EDIT && hasPermission(serverPlayer, FabricPermissionDefaults.DELETE_CLAIMS))
            {
                return true;
            }
            // Staff in /ignoreclaims mode pass whatever their bypass permission covers.
            if (isIgnoringClaims(serverPlayer.getUUID()) && hasPermission(serverPlayer,
                    required == ClaimTrustLevel.EDIT ? FabricPermissionDefaults.DELETE_CLAIMS
                            : FabricPermissionDefaults.IGNORE_CLAIMS))
            {
                return true;
            }
        }
        return allows(claim, player.getUUID(), required);
    }

    /**
     * Turns {@code /ignoreclaims} on or off for a player, as Paper's {@code PlayerData.ignoreClaims}.
     *
     * @return whether the player now ignores claims
     */
    boolean toggleIgnoringClaims(@NotNull UUID playerId)
    {
        if (this.ignoringClaims.remove(playerId))
        {
            return false;
        }
        this.ignoringClaims.add(playerId);
        return true;
    }

    boolean isIgnoringClaims(@NotNull UUID playerId)
    {
        return this.ignoringClaims.contains(playerId);
    }

    /** Ends a player's {@code /ignoreclaims} mode, as logging out does on Paper. */
    void stopIgnoringClaims(@NotNull UUID playerId)
    {
        this.ignoringClaims.remove(playerId);
    }

    /** @return whether a claim, or the claim a subdivision belongs to, has no owning player */
    synchronized boolean isAdminClaim(@NotNull ClaimSnapshot claim)
    {
        return effectiveOwnerId(claim) == null;
    }

    /** @return the player a claim answers to, walking up from a subdivision; null for admin claims */
    synchronized @Nullable UUID effectiveOwnerId(@NotNull ClaimSnapshot claim)
    {
        return ClaimOwnership.effectiveOwnerId(claim, this.claimIndex::get);
    }

    /**
     * Checks a GriefPrevention permission node, applying plugin.yml's defaults and parent nodes
     * where the permission provider has no value.
     */
    boolean hasPermission(@NotNull ServerPlayer player, @NotNull String permission)
    {
        UUID playerId = player.getUUID();
        return FabricPermissionDefaults.resolve(
                permission,
                node -> this.permissions.permissionValue(playerId, node),
                isOperator(player));
    }

    /**
     * @return the player's value for a permission node, or the given default when no permission
     *         provider decides it
     */
    boolean hasPermission(@NotNull ServerPlayer player, @NotNull String permission, boolean defaultValue)
    {
        return this.claimBlocks.permissionOrDefault(player.getUUID(), permission, defaultValue);
    }

    /**
     * @return the default Paper grants operators, for permissions whose plugin.yml default is op
     */
    static boolean isOperator(@NotNull ServerPlayer player)
    {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
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

        static @NotNull CreateClaimResult created(@NotNull ClaimSnapshot claim, @Nullable Integer remainingBlocks)
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
