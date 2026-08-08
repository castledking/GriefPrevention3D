package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Resolves the arbitrary permission identifiers stored in {@code PlayerData/$<permission>}. */
@FunctionalInterface
interface FabricGroupBonusPermissionResolver
{
    boolean hasPermission(@NotNull UUID playerId, @NotNull String permission);

    default @NotNull String description()
    {
        return "custom permission resolver";
    }
}
