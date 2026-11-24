package com.griefprevention.fabric;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The claim mode each player picked for the modification tool with {@code /basicclaims} or
 * {@code /adminclaims}. Like Paper's {@code PlayerData.shovelMode} it lasts for the session.
 */
final class FabricClaimModes
{
    enum Mode
    {
        BASIC,
        ADMIN,
        /** The shovel draws and reshapes orthogonal non-rectangular 2D claims. */
        SHAPED,
        /** The shovel draws 2D subdivisions inside claims, as Paper's {@code /subdivideclaims}. */
        SUBDIVIDE,
        /** The shovel draws height-limited subdivisions, as Paper's {@code /3dsubdivideclaims}. */
        SUBDIVIDE_3D,
        /** The shovel restores areas to nature. */
        RESTORE_NATURE,
        /** The shovel aggressively restores areas to nature, including claimed blocks. */
        RESTORE_NATURE_AGGRESSIVE,
        /** The shovel fills holes and restores nature with a custom radius. */
        RESTORE_NATURE_FILL;

        boolean subdivides()
        {
            return this == SUBDIVIDE || this == SUBDIVIDE_3D;
        }

        boolean isRestoreNature()
        {
            return this == RESTORE_NATURE || this == RESTORE_NATURE_AGGRESSIVE || this == RESTORE_NATURE_FILL;
        }
    }

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    void register()
    {
        ModPlatform.current().onPlayerDisconnect((player, server) ->
                this.modes.remove(player.getUUID()));
    }

    @NotNull Mode mode(@NotNull UUID playerId)
    {
        return this.modes.getOrDefault(playerId, Mode.BASIC);
    }

    /**
     * @return whether claims the player creates are administrative: admin mode, still backed by the
     *         permission, so a demoted staff member falls back to basic claims
     */
    boolean createsAdminClaims(@NotNull ServerPlayer player, @NotNull FabricClaimRepository claims)
    {
        return mode(player.getUUID()) == Mode.ADMIN
                && claims.hasPermission(player, FabricPermissionDefaults.ADMIN_CLAIMS);
    }

    void set(@NotNull UUID playerId, @NotNull Mode mode)
    {
        if (mode == Mode.BASIC)
        {
            this.modes.remove(playerId);
        }
        else
        {
            this.modes.put(playerId, mode);
        }
    }
}
