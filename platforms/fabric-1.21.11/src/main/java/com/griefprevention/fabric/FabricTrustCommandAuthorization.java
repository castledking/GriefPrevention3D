package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimTrustCommandPermissions;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Applies the Bukkit permission defaults and admin-child relationship on Fabric. */
final class FabricTrustCommandAuthorization
{
    private FabricTrustCommandAuthorization()
    {
    }

    static boolean canGrantManageTrust(
            @NotNull UUID playerId,
            @NotNull FabricPermissionResolver permissions)
    {
        Boolean direct = permissions.permissionValue(
                playerId,
                ClaimTrustCommandPermissions.MANAGE_TRUST
        );
        return direct == null || direct;
    }

    static boolean canGrantPermissionTrust(
            @NotNull UUID playerId,
            @NotNull FabricPermissionResolver permissions,
            boolean operatorDefault)
    {
        Boolean direct = permissions.permissionValue(
                playerId,
                ClaimTrustCommandPermissions.PERMISSION_TRUST
        );
        if (direct != null)
        {
            return direct;
        }

        Boolean parent = permissions.permissionValue(
                playerId,
                ClaimTrustCommandPermissions.ADMIN_CLAIMS
        );
        return parent == null ? operatorDefault : parent;
    }
}
