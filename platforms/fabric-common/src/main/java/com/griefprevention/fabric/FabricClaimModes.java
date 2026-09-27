package com.griefprevention.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        ADMIN
    }

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                this.modes.remove(handler.getPlayer().getUUID()));
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
