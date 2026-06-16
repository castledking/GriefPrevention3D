package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral query interface for claim repositories.
 *
 * <p>Implementations exist for each platform (Bukkit, Fabric).
 * Obtain an instance via platform-specific accessors.
 */
public interface ClaimRepository {

    /**
     * Get all claims.
     *
     * @return unmodifiable collection of all claim snapshots
     */
    @NotNull Collection<ClaimSnapshot> getClaims();

    /**
     * Get all claims owned by a specific player.
     *
     * @param owner the owner's UUID
     * @return unmodifiable collection of claim snapshots owned by the player
     */
    @NotNull Collection<ClaimSnapshot> getClaims(@NotNull UUID owner);

    /**
     * Get a claim by its ID.
     *
     * @param id the claim ID
     * @return the claim snapshot, or empty if not found
     */
    @NotNull Optional<ClaimSnapshot> getClaim(long id);

    /**
     * Find a claim at a specific location.
     *
     * @param worldKey the world key (namespace:world format)
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param ignoreHeight whether to ignore Y coordinate when searching
     * @param ignoreSubclaims whether to skip subdivisions in search
     * @return the claim snapshot, or empty if none found
     */
    @NotNull Optional<ClaimSnapshot> findClaimAt(
            @NotNull String worldKey,
            int x, int y, int z,
            boolean ignoreHeight,
            boolean ignoreSubclaims);

    /**
     * Find all candidate claims that may contain a location.
     *
     * @param worldKey the world key
     * @param bounds the bounds to search within
     * @return unmodifiable list of candidate claim snapshots
     */
    @NotNull Collection<ClaimSnapshot> candidates(@NotNull String worldKey, @NotNull ClaimBounds bounds);
}
