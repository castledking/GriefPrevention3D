package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Resolves arbitrary Fabric permission identifiers without requiring a provider. */
@FunctionalInterface
interface FabricPermissionResolver
{
    /** Returns {@code null} when no provider has an explicit value for this permission. */
    @Nullable Boolean permissionValue(@NotNull UUID playerId, @NotNull String permission);

    default boolean hasPermission(@NotNull UUID playerId, @NotNull String permission)
    {
        return Boolean.TRUE.equals(permissionValue(playerId, permission));
    }

    /**
     * Resolves a node for a player who may be offline, with plugin.yml's defaults and parents. A
     * provider that keeps offline players' data elsewhere loads it first, so the result may complete
     * on another thread.
     *
     * @param operator whether the player is a server operator, which an {@code op} default reads
     */
    default @NotNull CompletableFuture<Boolean> resolveOffline(
            @NotNull UUID playerId,
            @NotNull String permission,
            boolean operator)
    {
        return CompletableFuture.completedFuture(FabricPermissionDefaults.resolve(
                permission, node -> permissionValue(playerId, node), operator));
    }

    default @NotNull String description()
    {
        return "custom permission resolver";
    }
}
