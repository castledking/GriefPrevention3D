package com.griefprevention.fabric;

import com.griefprevention.commands.CommandAliasConfiguration;
import com.griefprevention.commands.CommandAliasConfiguration.RootCommand;
import com.griefprevention.commands.CommandAliasConfiguration.Subcommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the shared {@code alias.yml} configuration and registers Brigadier
 * command trees on Fabric, wiring subcommands to actual handlers.
 */
final class FabricCommandRegistrar
{
    private final FabricClaimRepository claims;
    private final FabricMessages messages;
    private final FabricDenialFeedback feedback;
    private @Nullable CommandDispatcher<CommandSourceStack> dispatcher;

    FabricCommandRegistrar(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricMessages messages,
            @NotNull FabricDenialFeedback feedback)
    {
        this.claims = claims;
        this.messages = messages;
        this.feedback = feedback;
    }

    void register(@NotNull CommandAliasConfiguration aliasConfig)
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
        {
            this.dispatcher = dispatcher;
            registerFromConfig(dispatcher, aliasConfig);
        });
    }

    void reload(@NotNull CommandAliasConfiguration aliasConfig)
    {
        if (this.dispatcher == null)
        {
            return;
        }
        registerFromConfig(this.dispatcher, aliasConfig);
    }

    private void registerFromConfig(
            @NotNull CommandDispatcher<CommandSourceStack> dispatcher,
            @NotNull CommandAliasConfiguration aliasConfig)
    {
        if (!aliasConfig.isEnabled())
        {
            return;
        }

        for (Map.Entry<String, RootCommand> entry : aliasConfig.getRootCommands().entrySet())
        {
            RootCommand root = entry.getValue();
            if (!root.isEnabled())
            {
                continue;
            }
            registerRootCommand(dispatcher, root, aliasConfig.isStandaloneEnabled());
        }
    }

    private void registerRootCommand(
            @NotNull CommandDispatcher<CommandSourceStack> dispatcher,
            @NotNull RootCommand root,
            boolean standaloneEnabled)
    {
        for (String rootName : root.getCommands())
        {
            var rootBuilder = Commands.literal(rootName)
                    .then(Commands.literal("help")
                            .executes(context -> sendHelp(context.getSource(), root)));

            for (Map.Entry<String, Subcommand> subEntry : root.getSubcommands().entrySet())
            {
                Subcommand sub = subEntry.getValue();
                if (!sub.isEnabled())
                {
                    continue;
                }

                CommandNode<CommandSourceStack> subNode = buildSubcommand(sub, root.getKey());
                rootBuilder.then(subNode);

                if (standaloneEnabled)
                {
                    for (String standaloneName : sub.getStandalone())
                    {
                        var standaloneBuilder = Commands.literal(standaloneName);
                        addArguments(standaloneBuilder, sub.getArguments());
                        Command<CommandSourceStack> handler = resolveHandler(sub.getKey(), sub.getArguments());
                        standaloneBuilder.executes(handler);
                        dispatcher.register(standaloneBuilder);
                    }
                }
            }

            dispatcher.register(rootBuilder);
        }
    }

    private @NotNull CommandNode<CommandSourceStack> buildSubcommand(
            @NotNull Subcommand sub,
            @NotNull String rootKey)
    {
        var builder = Commands.literal(sub.getKey());

        List<Subcommand.Argument> args = sub.getArguments();
        if (!args.isEmpty())
        {
            addArguments(builder, args);
        }

        Command<CommandSourceStack> handler = resolveHandler(sub.getKey(), sub.getArguments());
        builder.executes(handler);

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private @NotNull Command<CommandSourceStack> resolveHandler(
            @NotNull String subKey,
            @NotNull List<Subcommand.Argument> args)
    {
        return switch (subKey)
        {
            case "create" -> context -> FabricCommands.createClaim(
                    context.getSource(),
                    claims,
                    feedback,
                    getOptionalIntArg(context, args, "radius", 10));

            case "trust" -> context -> FabricCommands.trust(
                    context.getSource(),
                    claims,
                    feedback,
                    getRequiredStringArg(context, args, "player"),
                    getOptionalStringArg(context, args, "level", "access"));

            case "untrust" -> context -> FabricCommands.untrust(
                    context.getSource(),
                    claims,
                    feedback,
                    getRequiredStringArg(context, args, "player"));

            case "claimblocks" -> context -> FabricCommands.claimBlocks(
                    context.getSource(),
                    claims);

            case "claimslist" -> context -> FabricCommands.listClaims(
                    context.getSource(),
                    claims);

            case "abandon" -> context -> FabricCommands.abandonClaim(
                    context.getSource(),
                    claims,
                    feedback);

            case "claimhere" -> context -> FabricCommands.sendClaimHere(
                    context.getSource(),
                    claims,
                    feedback);

            case "pvp" -> context -> FabricCommands.claimPvp(
                    context.getSource(),
                    claims,
                    feedback,
                    getOptionalStringArg(context, args, "state", null),
                    getOptionalStringArg(context, args, "confirm", null));

            case "status" -> context -> FabricCommands.sendStatus(
                    context.getSource(),
                    claims);

            case "reload" -> context -> FabricCommands.reload(
                    context.getSource(),
                    claims,
                    messages,
                    feedback);

            default -> context -> {
                context.getSource().sendSuccess(() ->
                        Component.literal("Usage: /" + context.getInput()), false);
                return Command.SINGLE_SUCCESS;
            };
        };
    }

    private @Nullable String getRequiredStringArg(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            @NotNull List<Subcommand.Argument> args,
            @NotNull String name)
    {
        for (Subcommand.Argument arg : args)
        {
            if (name.equals(arg.name()) && arg.type() == null)
            {
                try
                {
                    return StringArgumentType.getString(context, name);
                }
                catch (Exception ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private @Nullable String getOptionalStringArg(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            @NotNull List<Subcommand.Argument> args,
            @NotNull String name,
            @Nullable String defaultValue)
    {
        for (Subcommand.Argument arg : args)
        {
            if (name.equals(arg.name()))
            {
                try
                {
                    return StringArgumentType.getString(context, name);
                }
                catch (Exception ignored)
                {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private int getOptionalIntArg(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            @NotNull List<Subcommand.Argument> args,
            @NotNull String name,
            int defaultValue)
    {
        for (Subcommand.Argument arg : args)
        {
            if (name.equals(arg.name()) && "integer".equals(arg.type()))
            {
                try
                {
                    return IntegerArgumentType.getInteger(context, name);
                }
                catch (Exception ignored)
                {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private void addArguments(
            @NotNull com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> builder,
            @NotNull List<Subcommand.Argument> args)
    {
        for (Subcommand.Argument arg : args)
        {
            String type = arg.type();

            if (type == null)
            {
                if (arg.suggestions().isEmpty())
                {
                    builder.then(Commands.argument(arg.name(), StringArgumentType.word()));
                }
                else
                {
                    List<String> suggestions = arg.suggestions();
                    builder.then(Commands.argument(arg.name(), StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestions, b)));
                }
            }
            else if (type.startsWith("["))
            {
                String literal = type.substring(1, type.length() - 1);
                builder.then(Commands.argument(arg.name(), IntegerArgumentType.integer(1))
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                new String[]{literal}, b)));
            }
            else if (type.contains("player") || type.equals("online-player"))
            {
                builder.then(Commands.argument(arg.name(), StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                ctx.getSource().getServer().getPlayerList().getPlayers()
                                        .stream()
                                        .map(p -> p.getName().getString())
                                        .toArray(String[]::new),
                                b)));
            }
            else if (type.equals("integer"))
            {
                builder.then(Commands.argument(arg.name(), IntegerArgumentType.integer(1)));
            }
            else if (type.equals("integer-negative"))
            {
                builder.then(Commands.argument(arg.name(), IntegerArgumentType.integer()));
            }
            else if (type.equals("string"))
            {
                builder.then(Commands.argument(arg.name(), StringArgumentType.word()));
            }
            else
            {
                if (arg.suggestions().isEmpty())
                {
                    builder.then(Commands.argument(arg.name(), StringArgumentType.word()));
                }
                else
                {
                    List<String> suggestions = arg.suggestions();
                    builder.then(Commands.argument(arg.name(), StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestions, b)));
                }
            }
        }
    }

    private int sendHelp(@NotNull CommandSourceStack source, @NotNull RootCommand root)
    {
        source.sendSuccess(() -> Component.literal(
                root.getDescription() != null ? root.getDescription() : "Command: /" + root.getKey()), false);
        source.sendSuccess(() -> Component.literal("Subcommands:"), false);
        for (Map.Entry<String, Subcommand> entry : root.getSubcommands().entrySet())
        {
            Subcommand sub = entry.getValue();
            if (sub.isEnabled())
            {
                String usage = sub.getUsage() != null ? sub.getUsage() : "/" + root.getKey() + " " + sub.getKey();
                String desc = sub.getDescription() != null ? sub.getDescription() : "";
                source.sendSuccess(() -> Component.literal("  " + usage + " - " + desc), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
