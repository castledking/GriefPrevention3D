package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

    default @NotNull String description()
    {
        return "custom permission resolver";
    }
}
