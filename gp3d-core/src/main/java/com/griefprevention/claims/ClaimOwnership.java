package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;

/** Resolves the owner a claim answers to, matching Bukkit's {@code Claim.getOwnerName()} walk. */
public final class ClaimOwnership
{
    private ClaimOwnership()
    {
    }

    /**
     * Walks up to the top-level claim to find the owning player.
     *
     * <p>Subdivisions written by older versions carry no owner of their own, so the parent decides.
     *
     * @param claim the claim a player interacted with
     * @param byId resolves a claim id to its snapshot, returning null when the id is unknown
     * @return the owning player, or null for an admin claim or a broken parent chain
     */
    public static @Nullable UUID effectiveOwnerId(
            @NotNull ClaimSnapshot claim,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        ClaimSnapshot current = claim;
        Set<Long> visited = new HashSet<>();
        while (true)
        {
            UUID ownerId = current.ownerId();
            if (ownerId != null)
            {
                return ownerId;
            }

            Long parentId = current.parentId();
            // A missing or already-seen parent means there is nothing further to inherit from; treat
            // the claim as ownerless rather than looping.
            if (parentId == null || !visited.add(parentId))
            {
                return null;
            }

            ClaimSnapshot parent = byId.apply(parentId);
            if (parent == null)
            {
                return null;
            }
            current = parent;
        }
    }
}
