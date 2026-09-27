package com.griefprevention.fabric;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Reads online-player permission state from LuckPerms when its Fabric mod is present. */
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

    @Override
    public @NotNull String description()
    {
        return "LuckPerms";
    }
}
