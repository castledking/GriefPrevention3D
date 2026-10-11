package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.commands.CommandAliasConfiguration;
import com.griefprevention.commands.CommandAliasConfiguration.RootCommand;
import com.griefprevention.commands.CommandAliasConfiguration.Subcommand;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Registers GriefPrevention's commands the way LuckPerms does on Fabric: each command is a Brigadier
 * literal with one greedy argument, and GriefPrevention splits and checks the arguments itself, as
 * Bukkit hands them to Paper's executors. That keeps {@code alias.yml}'s names, option aliases and
 * usage messages working as on Paper, and lets {@code [permission.node]} targets and negative
 * amounts through, which Brigadier's own argument types would reject.
 *
 * <p>{@code /claim} and {@code /aclaim} (and whatever {@code alias.yml} renames them to) dispatch to
 * subcommands; the standalone names come from {@code alias.yml} and Paper's plugin.yml. A failure
 * inside a command is logged with its stack trace rather than left to vanilla's debug-only log.
 */
final class FabricCommandRegistrar
{
    private static final String ARGUMENTS = "args";
    private static final Identifier AFTER_OTHER_MODS =
            Identifier.fromNamespaceAndPath(GriefPreventionFabric.MOD_ID, "after_other_mods");
    private static final String CLAIM_ROOT = "claim";
    private static final String ADMIN_ROOT = "aclaim";
    private static final String HELP = "help";
    private static final List<String> TRUST_TYPES = Arrays.asList("access", "container", "build", "manage");

    private final FabricClaimRepository claims;
    private final FabricMessages messages;
    private final FabricSettings settings;
    private final FabricDenialFeedback feedback;
    private final Path aliasFile;
    private final Logger logger;
    /** Implemented subcommands by root key, then subcommand key. */
    private final Map<String, Map<String, Action>> actions = new LinkedHashMap<>();
    /** Commands that exist on Paper through plugin.yml, whatever alias.yml says. */
    private final Map<String, Spec> legacyCommands = new LinkedHashMap<>();

    private volatile @NotNull Map<String, Spec> commands = Collections.emptyMap();
    private @Nullable CommandDispatcher<CommandSourceStack> dispatcher;
    private final Set<String> registeredLabels = new HashSet<>();

    FabricCommandRegistrar(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricMessages messages,
            @NotNull FabricSettings settings,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricTrustCommands trust,
            @NotNull FabricClaimCommands claimCommands,
            @NotNull FabricClaimBlockCommands blocks,
            @NotNull FabricAdminClaimCommands staff,
            @NotNull Path aliasFile,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.messages = messages;
        this.settings = settings;
        this.feedback = feedback;
        this.aliasFile = aliasFile;
        this.logger = logger;

        Completer players = (sender, previous) -> previous.length == 0 ? onlinePlayers(sender) : List.of();
        Completer trustTargets = (sender, previous) -> {
            if (previous.length == 0)
            {
                List<String> options = onlinePlayers(sender);
                options.add("public");
                return options;
            }
            return previous.length == 1 ? TRUST_TYPES : List.of();
        };
        Completer untrustTargets = (sender, previous) -> {
            if (previous.length != 0)
            {
                return List.of();
            }
            List<String> options = onlinePlayers(sender);
            options.add("all");
            options.add("public");
            return options;
        };

        Map<String, Action> claim = new LinkedHashMap<>();
        claim.put("create", new Action(claimCommands::create, FabricPermissionDefaults.CREATE_CLAIMS));
        claim.put("trust", new Action(trust::trust, FabricPermissionDefaults.TRUST));
        claim.put("untrust", new Action(trust::untrust, FabricPermissionDefaults.UNTRUST));
        claim.put("trustlist", new Action(trust::trustList, FabricPermissionDefaults.TRUST_LIST));
        claim.put("list", new Action(claimCommands::claimsList, FabricPermissionDefaults.CLAIMS_LIST));
        claim.put("mode", new Action(claimCommands::claimMode, FabricPermissionDefaults.BASIC_CLAIMS)
                .standalone(null));
        claim.put("abandon", new Action(claimCommands::abandon, FabricPermissionDefaults.ABANDON_CLAIM)
                .standalone(claimCommands::abandonClaim));
        claim.put("pvp", new Action(claimCommands::claimPvp, FabricPermissionDefaults.CLAIM_PVP));
        claim.put("explosions", new Action(claimCommands::claimExplosions, FabricPermissionDefaults.CLAIM_EXPLOSIONS));
        claim.put("restrictsubclaim", new Action(claimCommands::restrictSubclaim, FabricPermissionDefaults.RESTRICT_SUBCLAIM));
        claim.put("transfer", new Action(staff::transfer, FabricPermissionDefaults.TRANSFER_CLAIM));
        this.actions.put(CLAIM_ROOT, claim);

        Map<String, Action> admin = new LinkedHashMap<>();
        admin.put("trust", new Action(trust::adminPermissionTrust, FabricPermissionDefaults.PERMISSION_TRUST)
                .standalone(trust::permissionTrust));
        admin.put("mode", new Action(claimCommands::adminMode, FabricPermissionDefaults.ADMIN_CLAIMS));
        admin.put("blocks", new Action(blocks::adjust, FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS));
        admin.put("ignore", new Action(staff::ignoreClaims, FabricPermissionDefaults.IGNORE_CLAIMS));
        admin.put("adminlist", new Action(staff::adminClaimsList, FabricPermissionDefaults.ADMIN_CLAIMS_LIST));
        admin.put("delete", new Action(staff::delete, FabricPermissionDefaults.DELETE_CLAIMS));
        admin.put("transfer", new Action(staff::transfer, FabricPermissionDefaults.TRANSFER_CLAIM));
        admin.put("makeadmin", new Action(staff::makeAdmin, FabricPermissionDefaults.CONVERT_CLAIMS));
        admin.put("makebasic", new Action(staff::makeBasic, FabricPermissionDefaults.CONVERT_CLAIMS));
        this.actions.put(ADMIN_ROOT, admin);

        legacy("trust", trust::trust, FabricPermissionDefaults.TRUST,
                "/%s <player> [access|container|build|manage]", trustTargets, "tr");
        legacy("accesstrust", (sender, args) -> trust.trustAtLevel(sender, args, ClaimTrustLevel.ACCESS),
                FabricPermissionDefaults.ACCESS_TRUST, "/%s <player>", trustTargets, "at");
        legacy("containertrust", (sender, args) -> trust.trustAtLevel(sender, args, ClaimTrustLevel.CONTAINER),
                FabricPermissionDefaults.CONTAINER_TRUST, "/%s <player>", trustTargets, "ct");
        legacy("managetrust", (sender, args) -> trust.trustAtLevel(sender, args, ClaimTrustLevel.MANAGE),
                FabricPermissionDefaults.MANAGE_TRUST, "/%s <player>", trustTargets, "mt");
        legacy("permissiontrust", trust::permissionTrust, FabricPermissionDefaults.PERMISSION_TRUST,
                "/%s <permission> <access|container|build|manage>",
                (sender, previous) -> previous.length == 1 ? TRUST_TYPES : List.of(), "pt");
        legacy("untrust", trust::untrust, FabricPermissionDefaults.UNTRUST, "/%s <player|all|public>",
                untrustTargets, "ut");
        legacy("trustlist", trust::trustList, FabricPermissionDefaults.TRUST_LIST, "/%s", null);
        legacy("claimslist", claimCommands::claimsList, FabricPermissionDefaults.CLAIMS_LIST, "/%s [player]",
                (sender, previous) -> previous.length == 0
                        && sender.hasPermission(FabricPermissionDefaults.CLAIMS_LIST_OTHER)
                        ? onlinePlayers(sender) : List.of(),
                "claimlist", "listclaims");
        legacy("abandonclaim", claimCommands::abandonClaim, FabricPermissionDefaults.ABANDON_CLAIM, "/%s", null,
                "unclaim", "declaim", "removeclaim", "disclaim");
        legacy("abandontoplevelclaim", claimCommands::abandonTopLevelClaim,
                FabricPermissionDefaults.ABANDON_TOP_LEVEL_CLAIM, "/%s", null);
        legacy("abandonallclaims", claimCommands::abandonAllClaims, FabricPermissionDefaults.ABANDON_ALL_CLAIMS,
                "/%s confirm", (sender, previous) -> previous.length == 0 ? List.of("confirm") : List.of());
        legacy("claimpvp", claimCommands::claimPvp, FabricPermissionDefaults.CLAIM_PVP,
                "/%s [true|false|on|off] [confirm]",
                (sender, previous) -> previous.length == 0 ? List.of("true", "false", "on", "off")
                        : previous.length == 1 ? List.of("confirm") : List.of());
        legacy("claimpvpconfirm", claimCommands::claimPvpConfirm, FabricPermissionDefaults.CLAIM_PVP, "/%s", null);
        legacy("basicclaims", claimCommands::claimMode, FabricPermissionDefaults.BASIC_CLAIMS, "/%s", null, "bc");
        legacy("subdivideclaims", claimCommands::subdivideMode, FabricPermissionDefaults.SUBDIVIDE_CLAIMS, "/%s", null,
                "sc", "subdivideclaim");
        legacy("3dsubdivideclaims", claimCommands::subdivide3DMode, FabricPermissionDefaults.SUBDIVIDE_CLAIMS_3D, "/%s",
                null, "3dsubdivideclaim", "3dsc", "sc3d", "subdivide3d", "3dsubdivide");
        legacy("restrictsubclaim", claimCommands::restrictSubclaim, FabricPermissionDefaults.RESTRICT_SUBCLAIM, "/%s",
                null, "rsc");
        legacy("adminclaims", claimCommands::adminMode, FabricPermissionDefaults.ADMIN_CLAIMS, "/%s", null,
                "ac", "aclaims");
        legacy("adjustbonusclaimblocks", blocks::adjustBonus, FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS,
                "/%s <player|[permission]> <amount>", players, "acb");
        legacy("adjustbonusclaimblocksall", blocks::adjustBonusForAll, FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS,
                "/%s <amount>", null, "acball");
        legacy("setaccruedclaimblocks", blocks::setAccrued, FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS,
                "/%s <player> <amount>", players, "scb");
        legacy("setaccruedclaimblocksall", blocks::setAccruedForAll, FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS,
                "/%s <amount>", null, "scball");
        legacy("claimexplosions", claimCommands::claimExplosions, FabricPermissionDefaults.CLAIM_EXPLOSIONS,
                "/%s [on|off]", (sender, previous) -> previous.length == 0 ? List.of("on", "off") : List.of(),
                "claimexplosion");
        legacy("witherexplosions", claimCommands::witherExplosions, FabricPermissionDefaults.WITHER_EXPLOSIONS,
                "/%s [on|off]", (sender, previous) -> previous.length == 0 ? List.of("on", "off") : List.of(),
                "witherexplosion");
        legacy("transferclaim", staff::transfer, FabricPermissionDefaults.TRANSFER_CLAIM, "/%s <player> [confirm]",
                (sender, previous) -> previous.length == 0 ? onlinePlayers(sender)
                        : previous.length == 1 ? List.of("confirm") : List.of(),
                "giveclaim");
        legacy("ignoreclaims", staff::ignoreClaims, FabricPermissionDefaults.IGNORE_CLAIMS, "/%s", null, "ic");
        legacy("deleteclaim", staff::delete, FabricPermissionDefaults.DELETE_CLAIMS, "/%s", null);
        legacy("deleteallclaims", staff::deleteAllClaims, FabricPermissionDefaults.DELETE_CLAIMS, "/%s <player>", players);
        legacy("deletealladminclaims", staff::deleteAllAdminClaims, FabricPermissionDefaults.DELETE_ALL_ADMIN_CLAIMS,
                "/%s", null);
        legacy("adminclaimslist", staff::adminClaimsList, FabricPermissionDefaults.ADMIN_CLAIMS_LIST, "/%s", null);
        legacy("makeadmin", staff::makeAdmin, FabricPermissionDefaults.CONVERT_CLAIMS, "/%s", null);
        legacy("makebasic", staff::makeBasic, FabricPermissionDefaults.CONVERT_CLAIMS, "/%s", null);
        legacy("gpreload", this::reload, FabricPermissionDefaults.RELOAD, "/%s", null);
        legacy("gpstatus", this::status, FabricPermissionDefaults.RELOAD, "/%s", null);
    }

    void register()
    {
        this.commands = buildCommands(loadAliases());
        // Registering after other mods lets install() see their commands and step aside, instead of
        // another mod merging its command into a GriefPrevention name such as /create.
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_OTHER_MODS);
        CommandRegistrationCallback.EVENT.register(AFTER_OTHER_MODS, (dispatcher, buildContext, selection) -> {
            // A datapack reload builds a new dispatcher, and registration starts over with it.
            this.dispatcher = dispatcher;
            this.registeredLabels.clear();
            install(dispatcher);
        });
    }

    /**
     * Rereads alias.yml. New names are added to the live command tree; names that were removed stop
     * working and disappear from players' completions, but stay in the tree until the next restart.
     */
    private void reloadAliases(@NotNull MinecraftServer server)
    {
        this.commands = buildCommands(loadAliases());
        CommandDispatcher<CommandSourceStack> current = this.dispatcher;
        if (current != null)
        {
            install(current);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            server.getCommands().sendCommands(player);
        }
    }

    private @NotNull CommandAliasConfiguration loadAliases()
    {
        return CommandAliasConfiguration.load(this.aliasFile, new CommandAliasConfiguration.Logger()
        {
            @Override
            public void info(@NotNull String message)
            {
                FabricCommandRegistrar.this.logger.info(message);
            }

            @Override
            public void warning(@NotNull String message)
            {
                FabricCommandRegistrar.this.logger.warn(message);
            }

            @Override
            public void severe(@NotNull String message)
            {
                FabricCommandRegistrar.this.logger.error(message);
            }
        });
    }

    private void install(@NotNull CommandDispatcher<CommandSourceStack> dispatcher)
    {
        for (String label : this.commands.keySet())
        {
            if (this.registeredLabels.contains(label))
            {
                continue;
            }
            CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild(label);
            if (existing != null)
            {
                // Merging into another mod's or vanilla's node would replace what it runs.
                this.logger.warn("Not registering /{}: another command already uses that name.", label);
                continue;
            }

            LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(label)
                    .requires(source -> canUse(label, source))
                    .executes(context -> run(label, context.getSource(), ""));
            literal.then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                    .suggests((context, builder) -> suggest(label, context.getSource(), builder))
                    .executes(context -> run(label, context.getSource(),
                            StringArgumentType.getString(context, ARGUMENTS))));
            dispatcher.register(literal);
            this.registeredLabels.add(label);
        }
    }

    private boolean canUse(@NotNull String label, @NotNull CommandSourceStack source)
    {
        Spec spec = this.commands.get(label);
        return spec != null && (spec.permission == null || sender(source).hasPermission(spec.permission));
    }

    private int run(@NotNull String label, @NotNull CommandSourceStack source, @NotNull String rawArguments)
    {
        Spec spec = this.commands.get(label);
        FabricCommandSender sender = sender(source);
        if (spec == null)
        {
            sender.sendText(TextMode.ERROR, "/" + label + " is no longer configured; see alias.yml.");
            return 0;
        }

        try
        {
            if (!spec.handler.run(sender, FabricCommandLine.split(rawArguments)))
            {
                sendUsage(sender, spec.usage, null);
            }
        }
        catch (RuntimeException exception)
        {
            this.logger.error("/{} {} failed for {}.", label, rawArguments, sender.name(), exception);
            sender.sendText(TextMode.ERROR,
                    "Something went wrong running that command; the server log has the details.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private @NotNull CompletableFuture<Suggestions> suggest(
            @NotNull String label,
            @NotNull CommandSourceStack source,
            @NotNull SuggestionsBuilder builder)
    {
        Spec spec = this.commands.get(label);
        if (spec == null || spec.completer == null)
        {
            return builder.buildFuture();
        }

        FabricCommandLine.Completion completion = FabricCommandLine.completion(builder.getRemaining());
        List<String> options;
        try
        {
            options = spec.completer.complete(sender(source), completion.previous());
        }
        catch (RuntimeException exception)
        {
            this.logger.warn("Completing /{} failed.", label, exception);
            return builder.buildFuture();
        }

        SuggestionsBuilder current = builder.createOffset(builder.getStart() + completion.currentStart());
        for (String option : FabricCommandLine.matching(options, completion.current()))
        {
            current.suggest(option);
        }
        return current.buildFuture();
    }

    private @NotNull FabricCommandSender sender(@NotNull CommandSourceStack source)
    {
        return new FabricCommandSender(source, this.claims, this.messages);
    }

    // ---------------------------------------------------------------------------------------------
    // Building the commands from alias.yml and plugin.yml
    // ---------------------------------------------------------------------------------------------

    private @NotNull Map<String, Spec> buildCommands(@NotNull CommandAliasConfiguration aliases)
    {
        Map<String, Spec> result = new LinkedHashMap<>();
        // plugin.yml's commands come first: on Paper they exist whatever alias.yml says.
        for (Map.Entry<String, Spec> legacy : this.legacyCommands.entrySet())
        {
            result.put(legacy.getKey(), legacy.getValue());
        }

        if (!aliases.isEnabled())
        {
            // With the alias system off, Paper registers the English defaults.
            aliases = CommandAliasConfiguration.defaults(new SilentLogger());
        }

        for (String rootKey : Arrays.asList(CLAIM_ROOT, ADMIN_ROOT))
        {
            RootCommand root = aliases.getRootCommand(rootKey);
            if (root == null || !root.isEnabled())
            {
                continue;
            }
            Map<String, Action> rootActions = this.actions.get(rootKey);
            String rootPermission = root.getPermission();
            Spec rootSpec = new Spec(
                    (sender, args) -> runRoot(root, rootActions, sender, args),
                    rootPermission != null && !rootPermission.isBlank() ? rootPermission
                            : rootKey.equals(CLAIM_ROOT) ? FabricPermissionDefaults.CLAIMS
                            : FabricPermissionDefaults.ADMIN_CLAIMS,
                    "/" + rootKey + " help",
                    (sender, previous) -> completeRoot(root, rootActions, sender, previous));
            List<String> names = root.getCommands().isEmpty() ? List.of(rootKey) : root.getCommands();
            for (String name : names)
            {
                result.putIfAbsent(label(name), rootSpec);
            }

            if (!aliases.isStandaloneEnabled())
            {
                continue;
            }
            for (Map.Entry<String, Action> entry : rootActions.entrySet())
            {
                Subcommand sub = root.getSubcommand(entry.getKey());
                Action action = entry.getValue();
                if (sub == null || !sub.isEnabled() || action.standaloneHandler == null)
                {
                    continue;
                }
                Handler standalone = action.standaloneHandler;
                Spec spec = new Spec(
                        (sender, args) -> standalone.run(sender, sub.translate(args)),
                        permission(sub, action),
                        usage(rootKey, sub),
                        (sender, previous) -> completeArguments(sub, sender, previous));
                for (String name : sub.getStandalone())
                {
                    result.putIfAbsent(label(name), spec);
                }
            }
            if (rootKey.equals(CLAIM_ROOT))
            {
                Subcommand help = root.getSubcommand(HELP);
                if (help != null && help.isEnabled())
                {
                    Spec helpSpec = new Spec(
                            (sender, args) -> sendHelp(sender, root, rootActions),
                            null,
                            "/" + rootKey + " help",
                            null);
                    for (String name : help.getStandalone())
                    {
                        result.putIfAbsent(label(name), helpSpec);
                    }
                }
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private boolean runRoot(
            @NotNull RootCommand root,
            @NotNull Map<String, Action> rootActions,
            @NotNull FabricCommandSender sender,
            @NotNull String @NotNull [] args)
    {
        if (args.length == 0)
        {
            String fallback = root.getFallback();
            if (fallback != null && !fallback.isBlank())
            {
                String[] fallbackArgs = FabricCommandLine.split(fallback);
                if (fallbackArgs.length > 0)
                {
                    return runRoot(root, rootActions, sender, fallbackArgs);
                }
            }
            if (root.shouldUseAsHelpCmd() || !root.getKey().equals(CLAIM_ROOT))
            {
                return sendHelp(sender, root, rootActions);
            }
            // As on Paper, /claim on its own claims the land around the player.
            Action create = rootActions.get("create");
            return create == null ? sendHelp(sender, root, rootActions) : runAction(root, "create", create, sender, args);
        }

        String typed = args[0].toLowerCase(Locale.ROOT);
        String key = subcommandKey(root, typed);
        if (key == null)
        {
            sender.sendError(MessageKey.COMMAND_NOT_FOUND, typed);
            return true;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (key.equals(HELP))
        {
            return sendHelp(sender, root, rootActions);
        }
        Action action = rootActions.get(key);
        if (action == null)
        {
            sendUnavailable(sender);
            return true;
        }
        return runAction(root, key, action, sender, subArgs);
    }

    private boolean runAction(
            @NotNull RootCommand root,
            @NotNull String key,
            @NotNull Action action,
            @NotNull FabricCommandSender sender,
            @NotNull String @NotNull [] args)
    {
        Subcommand sub = root.getSubcommand(key);
        String configured = sub == null ? null : sub.getPermission();
        if (configured != null && !configured.isBlank() && !sender.checkPermission(configured))
        {
            return true;
        }
        String[] translated = sub == null ? args : sub.translate(args);
        if (!action.handler.run(sender, translated))
        {
            sendUsage(sender, sub == null ? "/" + root.getKey() + " " + key : usage(root.getKey(), sub),
                    root.getDescription());
        }
        return true;
    }

    /** @return the subcommand a typed name or alias.yml alias stands for, or null if none */
    private static @Nullable String subcommandKey(@NotNull RootCommand root, @NotNull String typed)
    {
        for (Map.Entry<String, Subcommand> entry : root.getSubcommands().entrySet())
        {
            Subcommand sub = entry.getValue();
            if (!sub.isEnabled())
            {
                continue;
            }
            if (entry.getKey().equals(typed))
            {
                return entry.getKey();
            }
            for (String alias : sub.getCommands())
            {
                if (alias.toLowerCase(Locale.ROOT).equals(typed))
                {
                    return entry.getKey();
                }
            }
        }
        return HELP.equals(typed) ? HELP : null;
    }

    private @NotNull List<String> completeRoot(
            @NotNull RootCommand root,
            @NotNull Map<String, Action> rootActions,
            @NotNull FabricCommandSender sender,
            @NotNull String @NotNull [] previous)
    {
        if (previous.length == 0)
        {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Subcommand> entry : sortedSubcommands(root).entrySet())
            {
                String key = entry.getKey();
                Action action = rootActions.get(key);
                boolean visible = key.equals(HELP) || (action != null && permitted(sender, entry.getValue(), action));
                if (visible)
                {
                    names.addAll(entry.getValue().getCommands().isEmpty()
                            ? List.of(key) : entry.getValue().getCommands());
                }
            }
            return names;
        }

        String key = subcommandKey(root, previous[0].toLowerCase(Locale.ROOT));
        Subcommand sub = key == null ? null : root.getSubcommand(key);
        Action action = key == null ? null : rootActions.get(key);
        if (sub == null || action == null || !permitted(sender, sub, action))
        {
            return List.of();
        }
        return completeArguments(sub, sender, Arrays.copyOfRange(previous, 1, previous.length));
    }

    /** Completes a subcommand from its alias.yml argument list: players, options or a literal hint. */
    private static @NotNull List<String> completeArguments(
            @NotNull Subcommand sub,
            @NotNull FabricCommandSender sender,
            @NotNull String @NotNull [] previous)
    {
        Subcommand.Argument argument = sub.getArgument(previous.length);
        if (argument == null)
        {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        String type = argument.type();
        if (type != null && (type.contains("player")))
        {
            options.addAll(onlinePlayers(sender));
        }
        else if (type != null && (type.startsWith("[") || type.startsWith("'")))
        {
            options.add(type.startsWith("'") && type.endsWith("'") && type.length() > 1
                    ? type.substring(1, type.length() - 1) : type);
        }
        options.addAll(argument.suggestions());
        return options;
    }

    private boolean sendHelp(
            @NotNull FabricCommandSender sender,
            @NotNull RootCommand root,
            @NotNull Map<String, Action> rootActions)
    {
        String description = root.getDescription();
        sender.sendText(TextMode.INFO, description == null || description.isBlank()
                ? "/" + root.getKey() : description);
        for (Map.Entry<String, Subcommand> entry : sortedSubcommands(root).entrySet())
        {
            Action action = rootActions.get(entry.getKey());
            Subcommand sub = entry.getValue();
            if (action == null || !permitted(sender, sub, action))
            {
                continue;
            }
            Component line = Component.literal(usage(root.getKey(), sub)).withStyle(ChatFormatting.YELLOW);
            String subDescription = sub.getDescription();
            if (subDescription != null && !subDescription.isBlank())
            {
                line = Component.empty().append(line)
                        .append(Component.literal(" - " + subDescription).withStyle(ChatFormatting.GRAY));
            }
            sender.sendComponent(line);
        }
        return true;
    }

    private static @NotNull Map<String, Subcommand> sortedSubcommands(@NotNull RootCommand root)
    {
        Map<String, Subcommand> sorted = new TreeMap<>();
        for (Map.Entry<String, Subcommand> entry : root.getSubcommands().entrySet())
        {
            if (entry.getValue().isEnabled())
            {
                sorted.put(entry.getKey(), entry.getValue());
            }
        }
        return sorted;
    }

    private static boolean permitted(
            @NotNull FabricCommandSender sender,
            @NotNull Subcommand sub,
            @NotNull Action action)
    {
        String permission = permission(sub, action);
        return permission == null || sender.hasPermission(permission);
    }

    private static @Nullable String permission(@NotNull Subcommand sub, @NotNull Action action)
    {
        String configured = sub.getPermission();
        return configured == null || configured.isBlank() ? action.permission : configured;
    }

    private static @NotNull String usage(@NotNull String rootKey, @NotNull Subcommand sub)
    {
        String usage = sub.getUsage();
        return usage == null || usage.isBlank() ? "/" + rootKey + " " + sub.getKey() : usage;
    }

    private static void sendUsage(
            @NotNull FabricCommandSender sender,
            @NotNull String usage,
            @Nullable String description)
    {
        sender.sendComponent(Component.literal("Usage: ").withStyle(ChatFormatting.RED)
                .append(Component.literal(usage).withStyle(ChatFormatting.YELLOW)));
        if (description != null && !description.isBlank())
        {
            sender.sendComponent(Component.literal(description).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tells the sender a Paper command has not been ported to Fabric yet. */
    static void sendUnavailable(@NotNull FabricCommandSender sender)
    {
        sender.sendText(TextMode.ERROR, "That command isn't available on Fabric yet.");
    }

    private static @NotNull List<String> onlinePlayers(@NotNull FabricCommandSender sender)
    {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : sender.server().getPlayerList().getPlayers())
        {
            names.add(FabricPlayerLookup.nameOf(player));
        }
        return names;
    }

    private static @NotNull String label(@NotNull String name)
    {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private void legacy(
            @NotNull String name,
            @NotNull Handler handler,
            @NotNull String permission,
            @NotNull String usage,
            @Nullable Completer completer,
            @NotNull String... aliases)
    {
        this.legacyCommands.put(name, new Spec(handler, permission, String.format(usage, name), completer));
        for (String alias : aliases)
        {
            this.legacyCommands.put(alias, new Spec(handler, permission, String.format(usage, alias), completer));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // /gpreload and /gpstatus
    // ---------------------------------------------------------------------------------------------

    private boolean reload(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.RELOAD))
        {
            return true;
        }
        int claimCount = this.claims.reload();
        this.messages.reload();
        this.settings.reload();
        this.feedback.clearRateLimits();
        MinecraftServer server = sender.server();
        // Changing the command tree while a command runs is unsafe; do it right after.
        server.execute(() -> reloadAliases(server));
        sender.sendText(TextMode.SUCCESS, "Configuration updated: " + claimCount
                + " claims, messages, settings and command aliases reloaded from " + this.claims.dataFolder() + ".");
        return true;
    }

    private boolean status(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.RELOAD))
        {
            return true;
        }
        sender.sendText(TextMode.INFO, "GriefPrevention3D Fabric claims: " + this.claims.claimCount()
                + " loaded from " + this.claims.dataFolder());
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------------------------------

    /** Runs a command with Bukkit-style arguments. */
    @FunctionalInterface
    interface Handler
    {
        /** @return false to show the command's usage, as a Bukkit executor does */
        boolean run(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args);
    }

    /** Suggests values for the argument after {@code previous}. */
    @FunctionalInterface
    interface Completer
    {
        @NotNull List<String> complete(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] previous);
    }

    /** A {@code /claim} or {@code /aclaim} subcommand Fabric implements. */
    private static final class Action
    {
        private final @NotNull Handler handler;
        private final @Nullable String permission;
        private @Nullable Handler standaloneHandler;

        private Action(@NotNull Handler handler, @Nullable String permission)
        {
            this.handler = handler;
            this.permission = permission;
            this.standaloneHandler = handler;
        }

        /** Sets what the subcommand's alias.yml standalone names run; null registers none. */
        private @NotNull Action standalone(@Nullable Handler handler)
        {
            this.standaloneHandler = handler;
            return this;
        }
    }

    /** One registered command name. */
    private static final class Spec
    {
        private final @NotNull Handler handler;
        private final @Nullable String permission;
        private final @NotNull String usage;
        private final @Nullable Completer completer;

        private Spec(
                @NotNull Handler handler,
                @Nullable String permission,
                @NotNull String usage,
                @Nullable Completer completer)
        {
            this.handler = handler;
            this.permission = permission;
            this.usage = usage;
            this.completer = completer;
        }
    }

    private static final class SilentLogger implements CommandAliasConfiguration.Logger
    {
        @Override
        public void info(@NotNull String message)
        {
        }

        @Override
        public void warning(@NotNull String message)
        {
        }

        @Override
        public void severe(@NotNull String message)
        {
        }
    }
}
