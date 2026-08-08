package com.griefprevention.fabric;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Reads online-player permission state from LuckPerms when its Fabric mod is present. */
final class LuckPermsGroupBonusPermissionResolver implements FabricGroupBonusPermissionResolver
{
    @Override
    public boolean hasPermission(@NotNull UUID playerId, @NotNull String permission)
    {
        try
        {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(playerId);
            return user != null
                    && user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean();
        }
        catch (IllegalStateException ignored)
        {
            // The API class may become visible before LuckPerms publishes its singleton during boot.
            return false;
        }
    }

    @Override
    public @NotNull String description()
    {
        return "LuckPerms";
    }
}
