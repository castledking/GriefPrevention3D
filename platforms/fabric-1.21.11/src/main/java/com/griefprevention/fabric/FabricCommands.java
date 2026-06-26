package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
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

    static void register(@NotNull FabricClaimRepository claims)
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                dispatcher.register(Commands.literal("gp3d")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("status")
                                .executes(context -> sendStatus(context.getSource(), claims)))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource(), claims)))
                        .then(Commands.literal("claimhere")
                                .executes(context -> sendClaimHere(context.getSource(), claims)))
                        .then(Commands.literal("claim")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                                .executes(context -> createClaim(
                                                        context.getSource(),
                                                        claims,
                                                        IntegerArgumentType.getInteger(context, "radius")))))
                                .then(Commands.literal("list")
                                        .executes(context -> listClaims(context.getSource(), claims)))
                                .then(Commands.literal("abandon")
                                        .executes(context -> abandonClaim(context.getSource(), claims)))
                                .then(Commands.literal("trust")
                                        .then(Commands.argument("target", StringArgumentType.word())
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
                                                                StringArgumentType.getString(context, "target"),
                                                                StringArgumentType.getString(context, "level"))))))
                                .then(Commands.literal("untrust")
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        targetSuggestions(context.getSource()),
                                                        builder))
                                                .executes(context -> untrust(
                                                        context.getSource(),
                                                        claims,
                                                        StringArgumentType.getString(context, "target"))))))));
    }

    private static int sendStatus(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
    {
        source.sendSuccess(() -> Component.literal("GriefPrevention3D Fabric claims: "
                + claims.claimCount()
                + " loaded from "
                + claims.dataFolder()), false);
        return claims.claimCount();
    }

    private static int reload(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
    {
        int claimCount = claims.reload();
        source.sendSuccess(() -> Component.literal("Reloaded "
                + claimCount
                + " GriefPrevention3D Fabric claims from "
                + claims.dataFolder()), true);
        return claimCount;
    }

    private static int createClaim(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
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
            if (!result.created())
            {
                ClaimSnapshot overlap = result.overlappingClaim();
                source.sendFailure(Component.literal("Cannot create claim; overlaps claim #"
                        + (overlap == null ? "unknown" : overlap.id())
                        + "."));
                return 0;
            }

            ClaimSnapshot created = result.createdClaim();
            source.sendSuccess(() -> Component.literal("Created GriefPrevention3D Fabric "
                    + formatClaim(created)), true);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("Could not save Fabric claim: " + e.getMessage()));
            return 0;
        }
    }

    private static int listClaims(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
    {
        List<ClaimSnapshot> snapshots = claims.snapshots();
        if (snapshots.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No GriefPrevention3D Fabric claims are loaded."), false);
            return 0;
        }

        int listed = Math.min(snapshots.size(), MAX_LISTED_CLAIMS);
        source.sendSuccess(() -> Component.literal("GriefPrevention3D Fabric claims: "
                + snapshots.size()
                + (snapshots.size() > listed ? " (showing first " + listed + ")" : "")), false);
        for (int i = 0; i < listed; i++)
        {
            ClaimSnapshot snapshot = snapshots.get(i);
            source.sendSuccess(() -> Component.literal(formatClaim(snapshot)), false);
        }
        return snapshots.size();
    }

    private static int abandonClaim(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        try
        {
            ClaimSnapshot deleted = claims.deleteClaimAt(source.getLevel(), player.blockPosition(), player);
            if (deleted == null)
            {
                source.sendFailure(Component.literal("No GriefPrevention3D Fabric claim found at your location."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Abandoned GriefPrevention3D Fabric claim #"
                    + deleted.id()
                    + "."), true);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException e)
        {
            source.sendFailure(Component.literal("Could not save Fabric claims after abandon: " + e.getMessage()));
            return 0;
        }
    }

    private static int trust(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
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

        String identifier = resolveTrustIdentifier(source, target);
        if (identifier == null)
        {
            source.sendFailure(Component.literal("Could not resolve target '" + target
                    + "'. Use public, a UUID, or an online player name."));
            return 0;
        }

        try
        {
            ClaimSnapshot claim = claims.setTrustAt(source.getLevel(), player.blockPosition(), identifier, level);
            if (claim == null)
            {
                source.sendFailure(Component.literal("No GriefPrevention3D Fabric claim found at your location."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Granted "
                    + level.name().toLowerCase(Locale.ROOT)
                    + " trust to "
                    + target
                    + " in claim #"
                    + claim.id()
                    + "."), true);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException | IllegalArgumentException e)
        {
            source.sendFailure(Component.literal("Could not update Fabric trust: " + e.getMessage()));
            return 0;
        }
    }

    private static int untrust(
            @NotNull CommandSourceStack source,
            @NotNull FabricClaimRepository claims,
            @NotNull String target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        String identifier = resolveTrustIdentifier(source, target);
        if (identifier == null)
        {
            source.sendFailure(Component.literal("Could not resolve target '" + target
                    + "'. Use public, a UUID, or an online player name."));
            return 0;
        }

        try
        {
            ClaimSnapshot claim = claims.removeTrustAt(source.getLevel(), player.blockPosition(), identifier);
            if (claim == null)
            {
                source.sendFailure(Component.literal("No GriefPrevention3D Fabric claim found at your location."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Removed trust for "
                    + target
                    + " in claim #"
                    + claim.id()
                    + "."), true);
            return Command.SINGLE_SUCCESS;
        }
        catch (IOException | IllegalArgumentException e)
        {
            source.sendFailure(Component.literal("Could not update Fabric trust: " + e.getMessage()));
            return 0;
        }
    }

    private static int sendClaimHere(@NotNull CommandSourceStack source, @NotNull FabricClaimRepository claims)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        ClaimSnapshot claim = claims.findClaimAt(source.getLevel(), pos);
        if (claim == null)
        {
            source.sendSuccess(() -> Component.literal("No GriefPrevention3D Fabric claim at "
                    + formatPosition(pos)
                    + "."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(formatClaim(claim)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull String formatClaim(@NotNull ClaimSnapshot claim)
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

    private static @NotNull String formatPosition(@NotNull BlockPos pos)
    {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static @Nullable ClaimTrustLevel parseTrustLevel(@NotNull String levelName)
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

    private static @Nullable String resolveTrustIdentifier(
            @NotNull CommandSourceStack source,
            @NotNull String target)
    {
        if ("public".equalsIgnoreCase(target))
        {
            return "public";
        }

        try
        {
            return UUID.fromString(target).toString();
        }
        catch (IllegalArgumentException ignored)
        {
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(target);
            return player == null ? null : player.getUUID().toString();
        }
    }

    private static @NotNull Iterable<String> targetSuggestions(@NotNull CommandSourceStack source)
    {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("public");
        suggestions.addAll(source.getOnlinePlayerNames());
        return suggestions;
    }
}
