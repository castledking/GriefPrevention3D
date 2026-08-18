package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimOwnership;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.messages.LegacyText;
import com.griefprevention.messages.MessageKey;
import com.griefprevention.messages.MessageRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells a player why a claim refused their action.
 *
 * <p>Follows Bukkit's split between throttled and unthrottled errors. {@link #denied} serves the
 * protection hooks, which fire from held-button interactions, so it throttles to one message per
 * player per ten seconds across all denials, matching {@code sendRateLimitedErrorMessage}. Claim tool
 * denials each need a deliberate click and go through {@link #sendError} unthrottled, as upstream does.
 */
final class FabricDenialFeedback
{
    private final FabricClaimRepository claims;
    private final FabricMessages messages;
    private final MessageRateLimiter rateLimiter = new MessageRateLimiter();

    FabricDenialFeedback(@NotNull FabricClaimRepository claims, @NotNull FabricMessages messages)
    {
        this.claims = claims;
        this.messages = messages;
    }

    void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null)
            {
                this.rateLimiter.forget(player.getUUID());
            }
        });
    }

    /**
     * Sends the denial message for the trust level the player was missing, if they are due one.
     */
    void denied(
            @NotNull Player player,
            @NotNull ClaimSnapshot claim,
            @NotNull ClaimTrustLevel required)
    {
        if (!(player instanceof ServerPlayer))
        {
            return;
        }

        ServerPlayer recipient = (ServerPlayer) player;
        // Claim the budget before resolving the owner: this runs every tick a player holds a button
        // against a protected block, and the name lookup is wasted work once throttled.
        if (!this.rateLimiter.tryAcquire(recipient.getUUID(), System.currentTimeMillis()))
        {
            return;
        }
        sendError(recipient, required.denialMessage(), ownerName(recipient, claim));
    }

    void sendError(
            @NotNull ServerPlayer player,
            @NotNull MessageKey key,
            @NotNull String @NotNull... args)
    {
        String message = this.messages.format(key, args);
        // An operator can blank a message in messages.yml to turn it off, as on Paper.
        if (LegacyText.isDisabled(message))
        {
            return;
        }
        player.sendSystemMessage(FabricLegacyComponents.toComponent(message, ChatFormatting.RED));
    }

    /**
     * @return the rendered message, for callers that deliver it themselves (command failures)
     */
    @NotNull Component component(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        return FabricLegacyComponents.toComponent(this.messages.format(key, args), ChatFormatting.RED);
    }

    private @NotNull String ownerName(@NotNull ServerPlayer viewer, @NotNull ClaimSnapshot claim)
    {
        UUID ownerId = ClaimOwnership.effectiveOwnerId(claim, this.claims::claimById);
        if (ownerId == null)
        {
            return this.messages.format(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS);
        }

        MinecraftServer server = viewer.level().getServer();
        if (server != null)
        {
            ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
            if (online != null)
            {
                return online.nameAndId().name();
            }

            // Only the local seen-player cache is consulted; a profile fetch would block the tick.
            Optional<NameAndId> cached = server.services().nameToIdCache().get(ownerId);
            if (cached.isPresent())
            {
                return cached.get().name();
            }
        }
        return ownerId.toString();
    }

    /** Clears throttles on reload so an operator testing message edits sees them immediately. */
    void clearRateLimits()
    {
        this.rateLimiter.clear();
    }
}
