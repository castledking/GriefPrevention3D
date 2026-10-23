package com.griefprevention.fabric;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.util.Tristate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads permission state from LuckPerms when its Fabric mod is present: from the users it has loaded,
 * which are the online players, or for an offline player from LuckPerms' storage.
 */
final class LuckPermsPermissionResolver implements FabricPermissionResolver
{
    @Override
    public @Nullable Boolean permissionValue(@NotNull UUID playerId, @NotNull String permission)
    {
        try
        {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(playerId);
            if (user == null)
            {
                return null;
            }
            Tristate value = user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission);
            return value == Tristate.UNDEFINED ? null : value.asBoolean();
        }
        catch (IllegalStateException ignored)
        {
            // The API class may become visible before LuckPerms publishes its singleton during boot.
            return null;
        }
    }

    /**
     * Loads an offline player's LuckPerms data, as claim expiration needs for
     * {@code griefprevention.dontexpire}, and unloads it again afterwards unless the player came online
     * meanwhile. Completes on a LuckPerms thread.
     */
    @Override
    public @NotNull CompletableFuture<Boolean> resolveOffline(
            @NotNull UUID playerId,
            @NotNull String permission,
            boolean operator)
    {
        final UserManager users;
        try
        {
            users = LuckPermsProvider.get().getUserManager();
        }
        catch (IllegalStateException ignored)
        {
            return FabricPermissionResolver.super.resolveOffline(playerId, permission, operator);
        }

        User loaded = users.getUser(playerId);
        if (loaded != null)
        {
            return CompletableFuture.completedFuture(resolve(loaded, permission, operator));
        }
        return users.loadUser(playerId).thenApply(user -> {
            try
            {
                return resolve(user, permission, operator);
            }
            finally
            {
                // Releases the data unless the player is online, as LuckPerms documents.
                users.cleanupUser(user);
            }
        });
    }

    private static boolean resolve(@NotNull User user, @NotNull String permission, boolean operator)
    {
        return FabricPermissionDefaults.resolve(permission, node -> {
            Tristate value = user.getCachedData().getPermissionData().checkPermission(node);
            return value == Tristate.UNDEFINED ? null : value.asBoolean();
        }, operator);
    }

    @Override
    public @NotNull String description()
    {
        return "LuckPerms";
    }
}
