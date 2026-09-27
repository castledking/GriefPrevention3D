package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/** Selects an optional Fabric permission provider without making it a hard mod dependency. */
final class FabricPermissions
{
    private FabricPermissions()
    {
    }

    static @NotNull FabricPermissionResolver detect(@NotNull Logger logger)
    {
        try
        {
            Class.forName(
                    "net.luckperms.api.LuckPermsProvider",
                    false,
                    FabricPermissions.class.getClassLoader()
            );
            logger.info("LuckPerms API detected; Fabric permission integration is enabled.");
            return new LuckPermsPermissionResolver();
        }
        catch (ClassNotFoundException | LinkageError ignored)
        {
            return new FabricPermissionResolver()
            {
                @Override
                public @org.jetbrains.annotations.Nullable Boolean permissionValue(
                        @NotNull java.util.UUID playerId,
                        @NotNull String permission)
                {
                    return null;
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
