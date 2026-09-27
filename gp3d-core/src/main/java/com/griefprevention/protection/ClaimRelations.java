package com.griefprevention.protection;

import com.griefprevention.claims.ClaimOwnership;
import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;

/** How two claims relate: the same claim, the same claim tree, or the same owner. */
final class ClaimRelations
{
    private ClaimRelations()
    {
    }

    static boolean sameClaim(@Nullable ClaimSnapshot first, @Nullable ClaimSnapshot second)
    {
        return first != null && second != null && Objects.equals(first.id(), second.id());
    }

    static @NotNull ClaimSnapshot root(@NotNull ClaimSnapshot claim, @NotNull LongFunction<ClaimSnapshot> byId)
    {
        ClaimSnapshot current = claim;
        Set<Long> visited = new HashSet<>();
        while (current.parentId() != null && visited.add(current.parentId()))
        {
            ClaimSnapshot parent = byId.apply(current.parentId());
            if (parent == null)
            {
                break;
            }
            current = parent;
        }
        return current;
    }

    /** Whether both claims belong to one top-level claim. Wilderness shares no tree. */
    static boolean sameTree(
            @Nullable ClaimSnapshot first,
            @Nullable ClaimSnapshot second,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        return first != null && second != null && sameClaim(root(first, byId), root(second, byId));
    }

    /** Whether both claims answer to the same owner; two admin claims do. */
    static boolean sameOwner(
            @Nullable ClaimSnapshot first,
            @Nullable ClaimSnapshot second,
            @NotNull LongFunction<ClaimSnapshot> byId)
    {
        if (first == null || second == null)
        {
            return false;
        }
        UUID firstOwner = ClaimOwnership.effectiveOwnerId(first, byId);
        UUID secondOwner = ClaimOwnership.effectiveOwnerId(second, byId);
        return Objects.equals(firstOwner, secondOwner);
    }
}
