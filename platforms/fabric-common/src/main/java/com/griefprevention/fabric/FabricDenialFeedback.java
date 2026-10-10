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
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Sends players the claim messages from messages.yml, most importantly why a claim refused their
 * action.
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
        send(player, TextMode.ERROR, key, args);
    }

    /** Sends a message in the color Paper's matching {@code TextMode} uses. */
    void send(
            @NotNull ServerPlayer player,
            @NotNull TextMode mode,
            @NotNull MessageKey key,
            @NotNull String @NotNull... args)
    {
        sendText(player, mode, this.messages.format(key, args));
    }

    /** Sends text that has no messages.yml key, such as claim dimensions. */
    void sendText(@NotNull ServerPlayer player, @NotNull TextMode mode, @NotNull String message)
    {
        // An operator can blank a message in messages.yml to turn it off, as on Paper.
        if (LegacyText.isDisabled(message))
        {
            return;
        }
        player.sendSystemMessage(FabricLegacyComponents.toComponent(message, mode.color));
    }

    /**
     * Sends a throttled error that no protection hook produced, sharing the denial budget, as
     * Paper's {@code sendRateLimitedErrorMessage} does.
     */
    void sendRateLimitedError(
            @NotNull ServerPlayer player,
            @NotNull MessageKey key,
            @NotNull String @NotNull... args)
    {
        if (this.rateLimiter.tryAcquire(player.getUUID(), System.currentTimeMillis()))
        {
            sendError(player, key, args);
        }
    }

    /**
     * @return the rendered message, for callers that deliver it themselves (command failures)
     */
    @NotNull Component component(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        return FabricLegacyComponents.toComponent(this.messages.format(key, args), ChatFormatting.RED);
    }

    /**
     * @return the claim owner's name as players should read it: the admin-claim label, the name of
     *         an online or previously seen owner, or failing both the owner's UUID
     */
    @NotNull String ownerName(@NotNull ServerPlayer viewer, @NotNull ClaimSnapshot claim)
    {
        UUID ownerId = ClaimOwnership.effectiveOwnerId(claim, this.claims::claimById);
        if (ownerId == null)
        {
            return this.messages.format(MessageKey.OWNER_NAME_FOR_ADMIN_CLAIMS);
        }

        MinecraftServer server = viewer.level().getServer();
        // Only local data is consulted; a profile fetch would block the tick.
        String known = server == null ? null : FabricPlayerLookup.knownName(server, ownerId);
        return known != null ? known : ownerId.toString();
    }

    /** Clears throttles on reload so an operator testing message edits sees them immediately. */
    void clearRateLimits()
    {
        this.rateLimiter.clear();
    }

    /** The colors of Paper's {@code TextMode}. */
    enum TextMode
    {
        INFO(ChatFormatting.AQUA),
        INSTRUCTION(ChatFormatting.YELLOW),
        WARNING(ChatFormatting.GOLD),
        ERROR(ChatFormatting.RED),
        SUCCESS(ChatFormatting.GREEN);

        private final @NotNull ChatFormatting color;

        TextMode(@NotNull ChatFormatting color)
        {
            this.color = color;
        }

        @NotNull ChatFormatting color()
        {
            return this.color;
        }
    }
}
