package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustIdentifier;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /trust}, {@code /untrust}, {@code /trustlist} and their per-level variants, following
 * Paper's {@code handleTrustCommand} and {@code handleUntrustCommand}: standing in a claim changes
 * that claim and needs manage trust there; standing outside changes every claim the player owns.
 */
final class FabricTrustCommands
{
    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;

    FabricTrustCommands(@NotNull FabricClaimRepository claims, @NotNull FabricDenialFeedback feedback)
    {
        this.claims = claims;
        this.feedback = feedback;
    }

    /** {@code /claim trust <player> [type]} and {@code /claim trust permission <node> <type>}. */
    boolean trust(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length == 3 && "permission".equalsIgnoreCase(args[0]))
        {
            return grantPermissionTrust(sender, player, args[1], args[2]);
        }
        if (args.length < 1 || args.length > 2)
        {
            return false;
        }

        // A bare /trust grants build trust, as on Paper.
        ClaimTrustLevel level = args.length == 1 ? ClaimTrustLevel.BUILD : trustType(args[1]);
        if (level == null)
        {
            return false;
        }
        grant(sender, player, level, args[0]);
        return true;
    }

    /** {@code /accesstrust}, {@code /containertrust} and {@code /managetrust}: one player argument. */
    boolean trustAtLevel(
            @NotNull FabricCommandSender sender,
            @NotNull String @NotNull [] args,
            @NotNull ClaimTrustLevel level)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length != 1)
        {
            return false;
        }
        grant(sender, player, level, args[0]);
        return true;
    }

    /** {@code /aclaim trust permission <node> <type>}. */
    boolean adminPermissionTrust(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (args.length != 3 || !"permission".equalsIgnoreCase(args[0]))
        {
            return false;
        }
        ServerPlayer player = sender.requirePlayer();
        return player == null || grantPermissionTrust(sender, player, args[1], args[2]);
    }

    /** {@code /permissiontrust <node> <type>}. */
    boolean permissionTrust(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (args.length != 2)
        {
            return false;
        }
        ServerPlayer player = sender.requirePlayer();
        return player == null || grantPermissionTrust(sender, player, args[0], args[1]);
    }

    private boolean grantPermissionTrust(
            @NotNull FabricCommandSender sender,
            @NotNull ServerPlayer player,
            @NotNull String node,
            @NotNull String type)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.PERMISSION_TRUST))
        {
            return true;
        }
        String identifier = ClaimTrustIdentifier.fromPermissionTarget(node);
        if (identifier == null)
        {
            sender.sendError(MessageKey.INVALID_PERMISSION_ID);
            return true;
        }
        ClaimTrustLevel level = trustType(type);
        if (level == null)
        {
            return false;
        }
        grant(sender, player, level, identifier);
        return true;
    }

    private void grant(
            @NotNull FabricCommandSender sender,
            @NotNull ServerPlayer player,
            @NotNull ClaimTrustLevel level,
            @NotNull String recipient)
    {
        if (!sender.checkPermission(commandPermission(level)))
        {
            return;
        }

        Recipient target = resolveRecipient(sender, recipient, false);
        if (target == null)
        {
            return;
        }
        if (target.permissionNode && !sender.checkPermission(FabricPermissionDefaults.PERMISSION_TRUST))
        {
            return;
        }

        ClaimSnapshot claim = claimAt(player);
        List<ClaimSnapshot> targets;
        if (claim == null)
        {
            targets = this.claims.topLevelClaimsOwnedBy(player.getUUID());
        }
        else
        {
            if (!this.claims.allows(claim, player, ClaimTrustLevel.MANAGE))
            {
                sender.sendError(MessageKey.NO_MANAGE_TRUST, this.feedback.ownerName(player, claim));
                return;
            }
            targets = Collections.singletonList(claim);
        }

        try
        {
            this.claims.grantTrust(targets, target.identifier, level);
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not save the trust change: " + exception.getMessage());
            return;
        }

        sender.send(
                TextMode.SUCCESS,
                MessageKey.GRANT_PERMISSION_CONFIRMATION,
                target.displayName,
                sender.format(permissionDescription(level)),
                sender.format(claim == null ? MessageKey.LOCATION_ALL_CLAIMS : MessageKey.LOCATION_CURRENT_CLAIM));
    }

    /** {@code /untrust <player|all|public|[permission]>}. */
    boolean untrust(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length != 1)
        {
            return false;
        }

        ClaimSnapshot claim = claimAt(player);
        boolean clearAll = "all".equals(args[0]);
        if (clearAll && claim != null && !this.claims.allows(claim, player, ClaimTrustLevel.EDIT))
        {
            sender.sendError(MessageKey.CLEAR_PERMS_OWNER_ONLY);
            return true;
        }

        Recipient target = null;
        if (!clearAll)
        {
            target = resolveRecipient(sender, args[0], true);
            if (target == null)
            {
                return true;
            }
        }

        try
        {
            if (claim == null)
            {
                List<ClaimSnapshot> owned = this.claims.topLevelClaimsOwnedBy(player.getUUID());
                if (target == null)
                {
                    this.claims.clearTrust(owned);
                    sender.send(TextMode.SUCCESS, MessageKey.UNTRUST_EVERYONE_ALL_CLAIMS);
                }
                else
                {
                    this.claims.revokeTrust(owned, target.identifier);
                    sender.send(TextMode.SUCCESS, MessageKey.UNTRUST_INDIVIDUAL_ALL_CLAIMS, target.displayName);
                }
                return true;
            }

            if (!this.claims.allows(claim, player, ClaimTrustLevel.MANAGE))
            {
                sender.sendError(MessageKey.NO_MANAGE_TRUST, this.feedback.ownerName(player, claim));
                return true;
            }

            List<ClaimSnapshot> here = Collections.singletonList(claim);
            if (target == null)
            {
                this.claims.clearTrust(here);
                sender.send(TextMode.SUCCESS, MessageKey.CLEAR_PERMISSIONS_ONE_CLAIM);
                return true;
            }

            ClaimTrustSnapshot trust = this.claims.trustFor(claim);
            boolean targetIsManager = trust != null
                    && trust.managerIdentifiers().contains(ClaimTrustSnapshot.normalizeIdentifier(target.identifier));
            if (targetIsManager && !this.claims.allows(claim, player, ClaimTrustLevel.EDIT))
            {
                // Only the owner may demote a manager.
                sender.sendError(MessageKey.MANAGERS_DONT_UNTRUST_MANAGERS, this.feedback.ownerName(player, claim));
                return true;
            }

            this.claims.revokeTrust(here, target.identifier);
            sender.send(TextMode.SUCCESS, MessageKey.UNTRUST_INDIVIDUAL_SINGLE_CLAIM, target.displayName);
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not save the trust change: " + exception.getMessage());
        }
        return true;
    }

    /** {@code /trustlist}: who holds which trust in the claim the player stands in. */
    boolean trustList(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }

        ClaimSnapshot claim = claimAt(player);
        if (claim == null)
        {
            sender.sendError(MessageKey.TRUST_LIST_NO_CLAIM);
            return true;
        }
        if (!this.claims.allows(claim, player, ClaimTrustLevel.MANAGE))
        {
            sender.sendError(MessageKey.NO_MANAGE_TRUST, this.feedback.ownerName(player, claim));
            return true;
        }

        ClaimTrustSnapshot trust = this.claims.trustFor(claim);
        if (trust == null)
        {
            trust = ClaimTrustSnapshot.empty(claim.ownerId());
        }
        List<String> builders = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        for (Map.Entry<String, ClaimTrustLevel> entry : trust.permissionsByIdentifier().entrySet())
        {
            switch (entry.getValue())
            {
                case BUILD -> builders.add(entry.getKey());
                case CONTAINER -> containers.add(entry.getKey());
                case ACCESS -> accessors.add(entry.getKey());
                default ->
                {
                }
            }
        }

        MinecraftServer server = sender.server();
        sender.send(TextMode.INFO, MessageKey.TRUST_LIST_HEADER, this.feedback.ownerName(player, claim));
        sendTrustLine(sender, server, ChatFormatting.GOLD, new ArrayList<>(trust.managerIdentifiers()));
        sendTrustLine(sender, server, ChatFormatting.YELLOW, builders);
        sendTrustLine(sender, server, ChatFormatting.GREEN, containers);
        sendTrustLine(sender, server, ChatFormatting.BLUE, accessors);
        List<String> neighbors = new ArrayList<>(trust.neighborIdentifiers());
        if (!neighbors.isEmpty())
        {
            sendTrustLine(sender, server, ChatFormatting.LIGHT_PURPLE, neighbors);
        }

        MutableComponent legend = Component.empty()
                .append(Component.literal(sender.format(MessageKey.MANAGE)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" " + sender.format(MessageKey.BUILD)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" " + sender.format(MessageKey.CONTAINERS)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" " + sender.format(MessageKey.ACCESS)).withStyle(ChatFormatting.BLUE));
        if (!neighbors.isEmpty())
        {
            legend.append(Component.literal(" " + sender.format(MessageKey.NEIGHBOR)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        sender.sendComponent(legend);

        if (this.claims.isRestrictedSubdivision(claim))
        {
            sender.sendError(MessageKey.HAS_SUBCLAIM_RESTRICTION);
        }
        return true;
    }

    private static void sendTrustLine(
            @NotNull FabricCommandSender sender,
            @NotNull MinecraftServer server,
            @NotNull ChatFormatting color,
            @NotNull List<String> identifiers)
    {
        StringBuilder line = new StringBuilder(">");
        for (String identifier : identifiers)
        {
            line.append(displayName(server, identifier)).append(' ');
        }
        sender.sendComponent(Component.literal(line.toString()).withStyle(color));
    }

    /** Paper's {@code trustEntryToPlayerName}: permission nodes and "public" as stored, UUIDs as names. */
    private static @NotNull String displayName(@NotNull MinecraftServer server, @NotNull String identifier)
    {
        if (identifier.startsWith("[") || ClaimTrustSnapshot.PUBLIC_IDENTIFIER.equals(identifier))
        {
            return identifier;
        }
        try
        {
            return FabricPlayerLookup.name(server, UUID.fromString(identifier));
        }
        catch (IllegalArgumentException notAUuid)
        {
            return identifier;
        }
    }

    /**
     * Resolves who is being trusted: a {@code [permission]}, "public", or a player. A name no player
     * has is read as a permission node when it contains a dot, as on Paper.
     *
     * @return the recipient, or null after telling the sender why none was found
     */
    private @Nullable Recipient resolveRecipient(
            @NotNull FabricCommandSender sender,
            @NotNull String recipient,
            boolean untrusting)
    {
        if (recipient.startsWith("[") || recipient.endsWith("]"))
        {
            String identifier = ClaimTrustIdentifier.fromPermissionTarget(recipient);
            if (identifier == null)
            {
                sender.sendError(MessageKey.INVALID_PERMISSION_ID);
                return null;
            }
            return new Recipient(identifier, identifier, true);
        }
        if (ClaimTrustSnapshot.PUBLIC_IDENTIFIER.equalsIgnoreCase(recipient))
        {
            return new Recipient(
                    ClaimTrustSnapshot.PUBLIC_IDENTIFIER,
                    sender.format(MessageKey.COLLECTIVE_PUBLIC),
                    false);
        }

        FabricPlayerLookup.KnownPlayer player = FabricPlayerLookup.find(sender.server(), recipient);
        if (player != null)
        {
            return new Recipient(player.id().toString(), player.name(), false);
        }

        String identifier = ClaimTrustIdentifier.fromPermissionTarget(recipient);
        if (identifier == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return null;
        }
        return new Recipient(identifier, untrusting ? recipient : identifier, true);
    }

    private @Nullable ClaimSnapshot claimAt(@NotNull ServerPlayer player)
    {
        return this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
    }

    /**
     * @return the trust level a {@code /claim trust} type names, or null if it names none
     */
    static @Nullable ClaimTrustLevel trustType(@NotNull String type)
    {
        return switch (type.trim().toLowerCase(Locale.ROOT))
        {
            case "access" -> ClaimTrustLevel.ACCESS;
            case "container", "inventory" -> ClaimTrustLevel.CONTAINER;
            case "build" -> ClaimTrustLevel.BUILD;
            case "manage", "manager" -> ClaimTrustLevel.MANAGE;
            default -> null;
        };
    }

    private static @NotNull String commandPermission(@NotNull ClaimTrustLevel level)
    {
        return switch (level)
        {
            case ACCESS -> FabricPermissionDefaults.ACCESS_TRUST;
            case CONTAINER -> FabricPermissionDefaults.CONTAINER_TRUST;
            case MANAGE -> FabricPermissionDefaults.MANAGE_TRUST;
            default -> FabricPermissionDefaults.TRUST;
        };
    }

    private static @NotNull MessageKey permissionDescription(@NotNull ClaimTrustLevel level)
    {
        return switch (level)
        {
            case MANAGE -> MessageKey.MANAGE_PERMISSION;
            case BUILD -> MessageKey.BUILD_PERMISSION;
            case ACCESS -> MessageKey.ACCESS_PERMISSION;
            default -> MessageKey.CONTAINERS_PERMISSION;
        };
    }

    private static final class Recipient
    {
        private final @NotNull String identifier;
        private final @NotNull String displayName;
        private final boolean permissionNode;

        private Recipient(@NotNull String identifier, @NotNull String displayName, boolean permissionNode)
        {
            this.identifier = identifier;
            this.displayName = displayName;
            this.permissionNode = permissionNode;
        }
    }
}
