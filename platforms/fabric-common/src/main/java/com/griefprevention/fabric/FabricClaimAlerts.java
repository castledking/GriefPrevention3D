package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimFlag;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.protection.BoundaryViolationAlerts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tells claim owners when a piston or a fluid was stopped at their claim's border, as Paper does,
 * unless they turned alerts off for that claim with {@code /claim alerts}. The deduplication and
 * burst summaries are {@link BoundaryViolationAlerts}'.
 */
final class FabricClaimAlerts
{
    private final BoundaryViolationAlerts alerts = new BoundaryViolationAlerts();
    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;

    FabricClaimAlerts(@NotNull FabricClaimRepository claims, @NotNull FabricDenialFeedback feedback)
    {
        this.claims = claims;
        this.feedback = feedback;
    }

    void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(this::deliver);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                this.alerts.forget(handler.getPlayer().getUUID()));
    }

    /**
     * Notes a refused move at a claim's border for its owner. Administrative claims have nobody to
     * tell, and an owner who is offline is not told later.
     *
     * @param claim the claim the piston or fluid would have entered or left
     * @param at where the owner should look
     */
    void record(
            @NotNull ServerLevel level,
            @NotNull ClaimSnapshot claim,
            @NotNull BlockPos at,
            @NotNull BoundaryViolationAlerts.Kind kind,
            @NotNull BoundaryViolationAlerts.Direction direction,
            int radius)
    {
        UUID ownerId = this.claims.effectiveOwnerId(claim);
        MinecraftServer server = level.getServer();
        if (ownerId == null || server == null || server.getPlayerList().getPlayer(ownerId) == null)
        {
            return;
        }
        Long claimId = claim.id();
        if (claimId != null && Boolean.FALSE.equals(this.claims.flag(claimId, ClaimFlag.ALERTS)))
        {
            return;
        }
        this.alerts.record(ownerId, this.claims.worldKey(level), at.getX(), at.getY(), at.getZ(),
                kind, direction, radius, System.currentTimeMillis());
    }

    private void deliver(@NotNull MinecraftServer server)
    {
        for (BoundaryViolationAlerts.Burst burst : this.alerts.due(System.currentTimeMillis()))
        {
            ServerPlayer owner = server.getPlayerList().getPlayer(burst.owner());
            if (owner != null)
            {
                this.feedback.send(owner, TextMode.ERROR, burst.message(), burst.arguments());
            }
        }
    }
}
