package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimFlag;
import com.griefprevention.claims.ClaimOwnership;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustCommandPermissions;
import com.griefprevention.claims.ClaimTrustIdentifier;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.messages.MessageKey;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class FabricCommands
{
    private static final int MAX_LISTED_CLAIMS = 20;
    private static final String[] TRUST_LEVELS = {"access", "container", "build", "manage", "neighbor"};

    private FabricCommands()
    {
    }

    static void register(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricMessages messages,
            @NotNull FabricDenialFeedback feedback)
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
        {
            dispatcher.register(Commands.literal("createclaim")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                            .executes(context -> createClaim(
                                    context.getSource(),
                                    claims,
                                    feedback,
                                    IntegerArgumentType.getInteger(context, "radius")))));

            dispatcher.register(Commands.literal("trust")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    targetSuggestions(context.getSource()),
                                    builder))
                            .then(Commands.argument("level", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            TRUST_LEVELS,
                                            builder))
                                    .executes(context -> trust(
                                            context.getSource(),
                                            claims,
                                            feedback,
                                            StringArgumentType.getString(context, "target"),
                                            StringArgumentType.getString(context, "level"))))));

            dispatcher.register(Commands.literal("untrust")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("target", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    targetSuggestions(context.getSource()),
                                    builder))
                            .executes(context -> untrust(
                                    context.getSource(),
                                    claims,
                                    feedback,
                                    StringArgumentType.getString(context, "target")))));

            dispatcher.register(Commands.literal("claimblocks")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> claimBlocks(context.getSource(), claims)));

            dispatcher.register(Commands.literal("claimslist")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> listClaims(context.getSource(), claims)));

            dispatcher.register(Commands.literal("abandonclaim")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> abandonClaim(context.getSource(), claims, feedback)));

            dispatcher.register(Commands.literal("claimhere")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> sendClaimHere(context.getSource(), claims, feedback)));

            dispatcher.register(Commands.literal("claimpvp")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("state", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    new String[]{"true", "false", "on", "off", "enable", "disable"},
                                    builder))
                            .then(Commands.argument("confirm", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            new String[]{"confirm"},
                                            builder))
                                    .executes(context -> claimPvp(
                                            context.getSource(),
                                            claims,
                                            feedback,
                                            StringArgumentType.getString(context, "state"),
                                            StringArgumentType.getString(context, "confirm"))))
                            .executes(context -> claimPvp(
                                    context.getSource(),
                                    claims,
                                    feedback,
                                    StringArgumentType.getString(context, "state"),
                                    null)))
                    .executes(context -> claimPvp(
                            context.getSource(),
                            claims,
                            feedback,
                            null,
                            null)));

            dispatcher.register(Commands.literal("gpstatus")
                    .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .executes(context -> sendStatus(context.getSource(), claims)));

            dispatcher.register(Commands.literal("gpreload")
                    .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .executes(context -> reload(context.getSource(), claims, messages, feedback)));
        });
    }

    static int sendStatus(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
    {
        source.sendSuccess(() -> Component.literal("GriefPrevention3D Fabric claims: "
                + claims.claimCount()
                + " loaded from "
                + claims.dataFolder()), false);
        return claims.claimCount();
    }

    static int reload(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricMessages messages,
            @NotNull FabricDenialFeedback feedback)
    {
        int claimCount = claims.reload();
        messages.reload();
        feedback.clearRateLimits();
        source.sendSuccess(() -> Component.literal("Reloaded "
                + claimCount
                + " GriefPrevention3D Fabric claims and messages from "
                + claims.dataFolder()), false);
        return claimCount;
    }

    // Package-private handler methods for use by FabricCommandRegistrar

    static int createClaim(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        try
        {
            FabricClaimRepository.CreateClaimResult result = claims.createClaim(
                    source.getLevel(),
                    player.blockPosition(),
                    player.getUUID(),
                    radius,
                    player
            );
            if (result.hasReachedClaimCountLimit())
            {
                source.sendFailure(feedback.component(
                        MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT));
                return 0;
            }
            if (result.hasInsufficientClaimBlocks())
            {
                source.sendFailure(feedback.component(
                        MessageKey.CREATE_CLAIM_INSUFFICIENT_BLOCKS,
                        String.valueOf(result.blocksNeeded())));
                return 0;
            }
            if (!result.created())
            {
                source.sendFailure(feedback.component(MessageKey.CREATE_CLAIM_FAIL_OVERLAP_SHORT));
                return 0;
            }

            ClaimSnapshot created = result.createdClaim();
            source.sendSuccess(() -> Component.literal("Created claim "
                    + formatClaim(created)
                    + (result.remainingBlocks() == null
                    ? ""
                    : "; " + result.remainingBlocks() + " claim blocks remaining")), false);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("Could not save claim: " + e.getMessage()));
            return 0;
        }
    }

    static int claimBlocks(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        try
        {
            ClaimBlockBalance balance = claims.claimBlockBalance(player.getUUID());
            source.sendSuccess(() -> Component.literal("Claim blocks: "
                    + balance.totalEntitlement()
                    + " total - "
                    + balance.claimedArea()
                    + " claimed = "
                    + balance.remaining()
                    + " remaining."), false);
            return balance.remaining();
        }
        catch (IOException exception)
        {
            source.sendFailure(Component.literal(
                    "Could not safely read your claim-block balance: " + exception.getMessage()
            ));
            return 0;
        }
    }

    static int listClaims(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
    {
        List<ClaimSnapshot> snapshots = claims.snapshots();
        if (snapshots.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No claims are loaded."), false);
            return 0;
        }

        int listed = Math.min(snapshots.size(), MAX_LISTED_CLAIMS);
        source.sendSuccess(() -> Component.literal("Claims: "
                + snapshots.size()
                + (snapshots.size() > listed ? " (showing first " + listed + ")" : "")), false);
        for (int i = 0; i < listed; i++)
        {
            ClaimSnapshot snapshot = snapshots.get(i);
            source.sendSuccess(() -> Component.literal(formatClaim(snapshot)), false);
        }
        return snapshots.size();
    }

    static int abandonClaim(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        try
        {
            ClaimSnapshot deleted = claims.deleteClaimAt(source.getLevel(), player.blockPosition(), player);
            if (deleted == null)
            {
                source.sendFailure(feedback.component(MessageKey.NOT_YOUR_CLAIM));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Abandoned claim #"
                    + deleted.id()
                    + "."), false);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("Could not save claims after abandon: " + e.getMessage()));
            return 0;
        }
    }

    static int trust(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull String target,
            @NotNull String levelName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ClaimTrustLevel level = parseTrustLevel(levelName);
        if (level == null)
        {
            source.sendFailure(Component.literal("Unknown trust level: " + levelName));
            return 0;
        }

        if (level == ClaimTrustLevel.MANAGE && !claims.canGrantManageTrust(player))
        {
            source.sendFailure(Component.literal("You need "
                    + ClaimTrustCommandPermissions.MANAGE_TRUST
                    + " to grant manage trust."));
            return 0;
        }

        String identifier = resolveTrustIdentifier(source, target);
        if (identifier == null)
        {
            source.sendFailure(unresolvedTargetMessage(target));
            return 0;
        }
        if (ClaimTrustIdentifier.permissionNode(identifier) != null
                && !claims.canGrantPermissionTrust(player))
        {
            source.sendFailure(Component.literal("You need "
                    + ClaimTrustCommandPermissions.PERMISSION_TRUST
                    + " to grant trust to a permission node."));
            return 0;
        }

        try
        {
            ClaimSnapshot claim = claims.setTrustAt(source.getLevel(), player.blockPosition(), identifier, level);
            if (claim == null)
            {
                source.sendFailure(feedback.component(MessageKey.NOT_YOUR_CLAIM));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Granted "
                    + level.name().toLowerCase(Locale.ROOT)
                    + " trust to "
                    + target
                    + " in claim #"
                    + claim.id()
                    + "."), false);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException | IllegalArgumentException e)
        {
            source.sendFailure(Component.literal("Could not update trust: " + e.getMessage()));
            return 0;
        }
    }

    static int untrust(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull String target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        String identifier = resolveTrustIdentifier(source, target);
        if (identifier == null)
        {
            source.sendFailure(unresolvedTargetMessage(target));
            return 0;
        }

        try
        {
            ClaimSnapshot claim = claims.removeTrustAt(source.getLevel(), player.blockPosition(), identifier);
            if (claim == null)
            {
                source.sendFailure(feedback.component(MessageKey.NOT_YOUR_CLAIM));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Removed trust for "
                    + target
                    + " in claim #"
                    + claim.id()
                    + "."), false);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException | IllegalArgumentException e)
        {
            source.sendFailure(Component.literal("Could not update trust: " + e.getMessage()));
            return 0;
        }
    }

    static int sendClaimHere(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        ClaimSnapshot claim = claims.findClaimAt(source.getLevel(), pos);
        if (claim == null)
        {
            source.sendSuccess(() -> Component.literal("No claim at "
                    + formatPosition(pos)
                    + "."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(formatClaim(claim)), false);
        return Command.SINGLE_SUCCESS;
    }

    static int claimPvp(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @Nullable String stateArg,
            @Nullable String confirmArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();

        // Check if PvP toggle is enabled (requires economy integration)
        // For now, always enabled - economy fee system can be added later
        boolean pvpToggleEnabled = true;
        if (!pvpToggleEnabled)
        {
            source.sendFailure(feedback.component(MessageKey.PVP_TOGGLE_NOT_ENABLED));
            return 0;
        }

        // Find claim at player's location
        ClaimSnapshot claim = claims.findClaimAt(source.getLevel(), player.blockPosition());
        if (claim == null)
        {
            source.sendFailure(feedback.component(MessageKey.NOT_YOUR_CLAIM));
            return 0;
        }

        // Check if player has permission to toggle PvP (must be claim owner or have Edit trust)
        if (claim.ownerId() != null && !claim.ownerId().equals(player.getUUID()))
        {
            // Check if they have Edit trust
            if (!claims.allows(claim, player.getUUID(), ClaimTrustLevel.MANAGE))
            {
                source.sendFailure(feedback.component(MessageKey.ONLY_OWNERS_MODIFY_CLAIMS,
                        ClaimOwnership.effectiveOwnerId(claim, id -> null).toString()));
                return 0;
            }
        }

        // Get current PvP state from claim document
        Boolean currentPvpState = claims.flag(claim.id(), ClaimFlag.PVP);
        if (currentPvpState == null)
        {
            source.sendFailure(feedback.component(MessageKey.NOT_YOUR_CLAIM));
            return 0;
        }
        boolean toggleTo;

        if (stateArg == null)
        {
            toggleTo = !currentPvpState;
        }
        else
        {
            String normalized = stateArg.trim().toLowerCase(Locale.ROOT);
            switch (normalized)
            {
                case "true":
                case "on":
                case "enable":
                    toggleTo = true;
                    break;
                case "false":
                case "off":
                case "disable":
                    toggleTo = false;
                    break;
                default:
                    source.sendFailure(feedback.component(MessageKey.PVP_TOGGLE_USAGE));
                    return 0;
            }
        }

        // Check if already in requested state
        if (currentPvpState == toggleTo)
        {
            String claimType = claim.subdivision() ? "subdivision" : "claim";
            source.sendFailure(feedback.component(
                    toggleTo ? MessageKey.PVP_TOGGLE_ALREADY_ENABLED : MessageKey.PVP_TOGGLE_ALREADY_DISABLED,
                    claimType));
            return 0;
        }

        // For now, require confirm argument (no pending state system yet)
        boolean confirmed = "confirm".equalsIgnoreCase(confirmArg);
        if (!confirmed)
        {
            String claimType = claim.subdivision() ? "subdivision" : "claim";
            source.sendSuccess(() -> feedback.component(
                    toggleTo ? MessageKey.CONFIRM_PVP_TOGGLE_ENABLED_NO_FEE : MessageKey.CONFIRM_PVP_TOGGLE_DISABLED_NO_FEE,
                    claimType), false);
            source.sendSuccess(() -> Component.literal("Type /claimpvp "
                    + (toggleTo ? "true" : "false")
                    + " confirm to confirm."), false);
            return 0;
        }

        // Toggle PvP
        try
        {
            claims.setFlag(claim.id(), ClaimFlag.PVP, toggleTo);
            String claimType = claim.subdivision() ? "subdivision" : "claim";
            source.sendSuccess(() -> feedback.component(
                    toggleTo ? MessageKey.PVP_TOGGLE_ENABLED : MessageKey.PVP_TOGGLE_DISABLED,
                    claimType), false);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("Could not save PvP toggle: " + e.getMessage()));
            return 0;
        }
    }

    static @NotNull String formatClaim(@NotNull ClaimSnapshot claim)
    {
        ClaimBounds bounds = claim.bounds();
        return "Claim #"
                + claim.id()
                + " owner="
                + (claim.ownerId() == null ? "admin" : claim.ownerId())
                + " world="
                + claim.worldKey()
                + " bounds="
                + bounds.minX()
                + ","
                + bounds.minY()
                + ","
                + bounds.minZ()
                + " -> "
                + bounds.maxX()
                + ","
                + bounds.maxY()
                + ","
                + bounds.maxZ()
                + " 3d="
                + claim.threeDimensional();
    }

    static @NotNull String formatPosition(@NotNull BlockPos pos)
    {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static @Nullable ClaimTrustLevel parseTrustLevel(@NotNull String levelName)
    {
        String normalized = levelName.trim().toUpperCase(Locale.ROOT);
        if ("MANAGER".equals(normalized))
        {
            normalized = "MANAGE";
        }
        if ("INVENTORY".equals(normalized))
        {
            normalized = "CONTAINER";
        }

        try
        {
            ClaimTrustLevel level = ClaimTrustLevel.valueOf(normalized);
            return level == ClaimTrustLevel.EDIT ? null : level;
        }
        catch (IllegalArgumentException ignored)
        {
            return null;
        }
    }

    static @Nullable String resolveTrustIdentifier(
            @NotNull CommandSourceStack source,
            @NotNull String target)
    {
        return FabricTrustTargetResolver.resolve(target, name -> knownPlayerId(source, name));
    }

    static @Nullable UUID knownPlayerId(
            @NotNull CommandSourceStack source,
            @NotNull String name)
    {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null)
        {
            return online.getUUID();
        }
        return source.getServer()
                .services()
                .nameToIdCache()
                .get(name)
                .map(profile -> profile.id())
                .orElse(null);
    }

    static @NotNull Component unresolvedTargetMessage(@NotNull String target)
    {
        if (target.startsWith("[") || target.endsWith("]"))
        {
            return Component.literal("Invalid permission trust target '" + target
                    + "'. Use the complete form \"[permission.node]\".");
        }
        return Component.literal("No current or previously seen player named '" + target
                + "'. Use public, a UUID, or a permission node containing a dot.");
    }

    static @NotNull Iterable<String> targetSuggestions(@NotNull CommandSourceStack source)
    {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("public");
        suggestions.add(StringArgumentType.escapeIfRequired("[permission.node]"));
        suggestions.addAll(source.getOnlinePlayerNames());
        return suggestions;
    }
}
