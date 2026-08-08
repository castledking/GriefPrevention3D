package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/** Selects an optional Fabric permission provider without making it a hard mod dependency. */
final class FabricGroupBonusPermissions
{
    private FabricGroupBonusPermissions()
    {
    }

    static @NotNull FabricGroupBonusPermissionResolver detect(@NotNull Logger logger)
    {
        try
        {
            Class.forName(
                    "net.luckperms.api.LuckPermsProvider",
                    false,
                    FabricGroupBonusPermissions.class.getClassLoader()
            );
            logger.info("LuckPerms API detected; Fabric permission-group claim-block bonuses are enabled.");
            return new LuckPermsGroupBonusPermissionResolver();
        }
        catch (ClassNotFoundException | LinkageError ignored)
        {
            return new FabricGroupBonusPermissionResolver()
            {
                @Override
                public boolean hasPermission(
                        @NotNull java.util.UUID playerId,
                        @NotNull String permission)
                {
                    return false;
                }

                @Override
                public @NotNull String description()
                {
                    return "no permission provider";
                }
            };
        }
    }
}
