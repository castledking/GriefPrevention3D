package com.griefprevention.fabric;

import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.LegacyText;
import com.griefprevention.messages.MessageKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Whoever ran a GriefPrevention command, as Bukkit's {@code CommandSender}: a player, the console,
 * or a command block. Messages use messages.yml and Paper's {@code TextMode} colors either way.
 */
final class FabricCommandSender
{
    private final @NotNull CommandSourceStack source;
    private final @NotNull FabricClaimRepository claims;
    private final @NotNull FabricMessages messages;

    FabricCommandSender(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricMessages messages)
    {
        this.source = source;
        this.claims = claims;
        this.messages = messages;
    }

    /** @return the player who ran the command, or null for the console and command blocks */
    @Nullable ServerPlayer player()
    {
        return this.source.getPlayer();
    }

    /**
     * @return the player who ran the command; anything else is told the command needs a player
     */
    @Nullable ServerPlayer requirePlayer()
    {
        ServerPlayer player = player();
        if (player == null)
        {
            send(TextMode.ERROR, MessageKey.COMMAND_REQUIRES_PLAYER);
        }
        return player;
    }

    @NotNull String name()
    {
        return this.source.getTextName();
    }

    @NotNull MinecraftServer server()
    {
        return this.source.getServer();
    }

    /**
     * Checks a GriefPrevention permission node with plugin.yml's defaults. The console and command
     * blocks count as operators when their permission level allows game-master commands.
     */
    boolean hasPermission(@NotNull String permission)
    {
        ServerPlayer player = player();
        if (player != null)
        {
            return this.claims.hasPermission(player, permission);
        }
        return FabricPermissionDefaults.resolve(
                permission,
                node -> null,
                FabricVersionCompat.isGameMaster(this.source));
    }

    /**
     * Checks a permission, telling the sender Paper's no-permission message when it is missing.
     */
    boolean checkPermission(@NotNull String permission)
    {
        if (hasPermission(permission))
        {
            return true;
        }
        send(TextMode.ERROR, MessageKey.NO_PERMISSION_FOR_COMMAND);
        return false;
    }

    void send(@NotNull TextMode mode, @NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        sendText(mode, this.messages.format(key, args));
    }

    void sendError(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        send(TextMode.ERROR, key, args);
    }

    /** Sends text that has no messages.yml key. */
    void sendText(@NotNull TextMode mode, @NotNull String message)
    {
        // An operator can blank a message in messages.yml to turn it off, as on Paper.
        if (LegacyText.isDisabled(message))
        {
            return;
        }
        this.source.sendSystemMessage(FabricLegacyComponents.toComponent(message, mode.color()));
    }

    void sendComponent(@NotNull Component component)
    {
        this.source.sendSystemMessage(component);
    }

    /** @return the text of a message, for building larger messages out of messages.yml parts */
    @NotNull String format(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        return this.messages.format(key, args);
    }
}
