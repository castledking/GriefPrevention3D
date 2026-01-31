package com.griefprevention.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import me.ryanhamshire.GriefPrevention.Alias;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for unified command handlers that support subcommands
 */
public abstract class UnifiedCommandHandler implements TabExecutor {

    protected final GriefPrevention plugin;
    protected final Map<String, Object> subcommands = new HashMap<>();
    protected final Map<String, List<String>> subcommandAliases = new HashMap<>();
    protected final Map<String, String> aliasToCanonical = new HashMap<>();
    protected final Set<String> tabCompletionSuggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    protected final Map<String, CommandAliasConfiguration.Subcommand> subcommandConfigs = new HashMap<>();
    protected final String canonicalCommand;
    protected final CommandAliasConfiguration.RootCommand rootCommandConfig;
    protected final boolean rootCommandEnabled;

    // Track dynamically registered commands for cleanup on reload
    private static final List<String> registeredDynamicCommands = new ArrayList<>();
    private static final Object registrationLock = new Object();

    protected UnifiedCommandHandler(@NotNull GriefPrevention plugin, String command) {
        this.plugin = plugin;
        this.canonicalCommand = command.toLowerCase(Locale.ROOT);

        // Check if alias system is globally disabled
        if (!plugin.getCommandAliases().isEnabled()) {
            this.rootCommandConfig = null;
            this.rootCommandEnabled = true; // Use defaults when alias system disabled
            plugin
                .getLogger()
                .info("Initializing command '" + this.canonicalCommand + "' - Using defaults (alias system disabled)");
        } else {
            this.rootCommandConfig = plugin.getCommandAliases().getRootCommand(this.canonicalCommand);
            this.rootCommandEnabled = this.rootCommandConfig == null || this.rootCommandConfig.isEnabled();

            // Log the enable/disable state for debugging
            plugin
                .getLogger()
                .info(
                    "Initializing command '" +
                        this.canonicalCommand +
                        "' - Enabled: " +
                        this.rootCommandEnabled +
                        (this.rootCommandConfig != null ? " (from config)" : " (default enabled)")
                );
        }

        // Get the primary command name from config (first entry in commands list)
        String primaryCommandName = canonicalCommand;
        if (rootCommandConfig != null && !rootCommandConfig.getCommands().isEmpty()) {
            primaryCommandName = rootCommandConfig.getCommands().get(0).toLowerCase(Locale.ROOT);
        }

        // Check if we need to register a different primary command name
        boolean needsDynamicRegistration = !primaryCommandName.equals(canonicalCommand);

        PluginCommand pluginCommand = plugin.getCommand(this.canonicalCommand);
        if (pluginCommand == null) {
            // Try to register the command manually if not found in plugin.yml
            try {
                pluginCommand = plugin.getServer().getPluginCommand(this.canonicalCommand);
                if (pluginCommand == null) {
                    plugin
                        .getLogger()
                        .warning(
                            "Failed to find command '" +
                                this.canonicalCommand +
                                "' in plugin.yml. Some functionality may be limited."
                        );
                    return;
                }
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .warning(
                        "Error while trying to register command '" + this.canonicalCommand + "': " + e.getMessage()
                    );
                return;
            }
        }

        pluginCommand.setExecutor(this);
        applyRootCommandMetadata(pluginCommand);

        // If user configured a different primary command name, register it dynamically
        if (needsDynamicRegistration && rootCommandEnabled) {
            registerDynamicRootCommand(primaryCommandName);
        }
    }

    /**
     * Register a standalone command from the Alias enum.
     * This reads the actual standalone names from alias.yml configuration,
     * allowing users to customize command names dynamically.
     */
    protected void registerStandaloneCommand(
        @NotNull Alias alias,
        @NotNull BiFunction<CommandSender, String[], Boolean> handler
    ) {
        // If alias system is globally disabled, use enum defaults only
        if (!plugin.getCommandAliases().isEnabled()) {
            String enumStandalone = alias.getStandalone();
            if (enumStandalone == null || enumStandalone.isEmpty()) {
                return;
            }
            registerDynamicStandaloneCommand(enumStandalone, alias.toString(), handler, null);
            return;
        }

        // Get the subcommand name from the enum
        final String subcommandName = alias.toString().toLowerCase(Locale.ROOT);
        final String finalSubcommandName;
        if (subcommandName.startsWith("claim")) {
            finalSubcommandName = subcommandName.substring(5); // Remove "claim" prefix
        } else if (subcommandName.startsWith("aclaim")) {
            finalSubcommandName = subcommandName.substring(6); // Remove "aclaim" prefix
        } else {
            finalSubcommandName = subcommandName;
        }

        // Get standalone names from configuration (alias.yml), not from enum
        CommandAliasConfiguration.Subcommand config =
            rootCommandConfig != null ? rootCommandConfig.getSubcommand(finalSubcommandName) : null;

        List<String> standaloneNames;
        if (config != null && !config.getStandalone().isEmpty()) {
            standaloneNames = config.getStandalone();
        } else {
            // Fall back to enum default if no config
            String enumStandalone = alias.getStandalone();
            if (enumStandalone == null || enumStandalone.isEmpty()) {
                return;
            }
            standaloneNames = List.of(enumStandalone);
        }

        // Check if standalone is disabled (empty list in config means disabled)
        if (standaloneNames.isEmpty()) {
            plugin.getLogger().info("Standalone for '" + finalSubcommandName + "' is disabled in config");
            return;
        }

        // Register each standalone command name
        for (String standaloneName : standaloneNames) {
            if (standaloneName == null || standaloneName.isBlank()) continue;
            registerDynamicStandaloneCommand(standaloneName.trim(), finalSubcommandName, handler, config);
        }
    }

    /**
     * Dynamically register a standalone command using Bukkit's CommandMap.
     * This allows command names to be configured in alias.yml without being in plugin.yml.
     */
    private void registerDynamicStandaloneCommand(
        @NotNull String commandName,
        @NotNull String subcommandName,
        @NotNull BiFunction<CommandSender, String[], Boolean> handler,
        @Nullable CommandAliasConfiguration.Subcommand config
    ) {
        // First try to get from plugin.yml
        PluginCommand pluginCommand = plugin.getCommand(commandName);
        if (pluginCommand != null) {
            // Command exists in plugin.yml, just set the executor
            TabExecutor wrapper = createStandaloneWrapper(handler, subcommandName);
            pluginCommand.setExecutor(wrapper);
            pluginCommand.setTabCompleter(wrapper);
            applyStandaloneMetadata(pluginCommand, config);
            plugin.getLogger().info("Registered standalone command: " + commandName + " (from plugin.yml)");
            return;
        }

        // Command not in plugin.yml - register dynamically using CommandMap
        synchronized (registrationLock) {
            try {
                CommandMap map = getCommandMap();
                if (map == null) {
                    plugin.getLogger().warning("Failed to get CommandMap for dynamic command registration");
                    return;
                }

                // Create a dynamic command
                DynamicStandaloneCommand dynamicCommand = new DynamicStandaloneCommand(
                    commandName,
                    config != null ? config.getDescription() : "GriefPrevention command",
                    config != null ? config.getUsage() : "/" + commandName,
                    config != null ? config.getPermission() : "griefprevention.claims",
                    handler,
                    subcommandName
                );

                // Force-register by removing any existing command with this name first
                try {
                    Field knownCommandsField = map.getClass().getSuperclass().getDeclaredField("knownCommands");
                    knownCommandsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(map);

                    // Remove existing command if present (allows override)
                    String lowerName = commandName.toLowerCase();
                    knownCommands.remove(lowerName);

                    // Register our command
                    knownCommands.put(lowerName, dynamicCommand);
                    knownCommands.put(plugin.getName().toLowerCase() + ":" + lowerName, dynamicCommand);
                } catch (Exception e) {
                    // Fallback to normal registration if reflection fails
                    map.register(plugin.getName().toLowerCase(), dynamicCommand);
                }

                // Track for cleanup on reload
                registeredDynamicCommands.add(commandName);

                plugin.getLogger().info("Registered standalone command: " + commandName + " (dynamically)");
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .warning("Failed to dynamically register command '" + commandName + "': " + e.getMessage());
            }
        }
    }

    private TabExecutor createStandaloneWrapper(
        @NotNull BiFunction<CommandSender, String[], Boolean> handler,
        @NotNull String subcommandName
    ) {
        return new TabExecutor() {
            @Override
            public boolean onCommand(
                @NotNull CommandSender sender,
                @NotNull Command command,
                @NotNull String label,
                @NotNull String[] args
            ) {
                return handler.apply(sender, args);
            }

            @Override
            public @Nullable List<String> onTabComplete(
                @NotNull CommandSender sender,
                @NotNull Command command,
                @NotNull String alias,
                @NotNull String[] args
            ) {
                // For standalone commands, directly complete arguments for the specific subcommand
                // instead of treating it as a root command with subcommand selection
                String canonical = subcommandName; // subcommandName is already canonical
                Object handlerObj = subcommands.get(canonical);
                if (handlerObj instanceof TabExecutor) {
                    // Create a mock command for TabExecutor
                    org.bukkit.command.Command mockCommand = new org.bukkit.command.Command(canonical) {
                        @Override
                        public boolean execute(
                            @NotNull org.bukkit.command.CommandSender sender,
                            @NotNull String commandLabel,
                            @NotNull String[] args
                        ) {
                            return false;
                        }
                    };
                    return ((TabExecutor) handlerObj).onTabComplete(sender, mockCommand, alias, args);
                } else if (handlerObj != null) {
                    // Use the subcommand argument completion logic
                    return completeSubcommandArguments(sender, command, alias, canonical, args, args.length - 1);
                }
                return Collections.emptyList();
            }
        };
    }

    private void applyStandaloneMetadata(
        @NotNull PluginCommand pluginCommand,
        @Nullable CommandAliasConfiguration.Subcommand config
    ) {
        if (config == null) return;

        String description = config.getDescription();
        if (description != null && !description.isBlank()) {
            pluginCommand.setDescription(description);
        }

        String permission = config.getPermission();
        if (permission != null && !permission.isBlank()) {
            pluginCommand.setPermission(permission);
        }
    }

    /**
     * Register a legacy standalone command that exists in plugin.yml but not in alias system
     */
    protected void registerLegacyStandaloneCommand(
        @NotNull String commandName,
        @Nullable String permission,
        @NotNull BiFunction<CommandSender, String[], Boolean> handler
    ) {
        registerLegacyStandaloneCommand(commandName, permission, handler, Collections.emptyList());
    }

    /**
     * Register a legacy standalone command with custom tab completions
     */
    protected void registerLegacyStandaloneCommand(
        @NotNull String commandName,
        @Nullable String permission,
        @NotNull BiFunction<CommandSender, String[], Boolean> handler,
        @Nullable List<String> tabCompletions
    ) {
        PluginCommand pluginCommand = plugin.getCommand(commandName);
        if (pluginCommand != null) {
            TabExecutor wrapper = new TabExecutor() {
                @Override
                public boolean onCommand(
                    @NotNull CommandSender sender,
                    @NotNull Command command,
                    @NotNull String label,
                    @NotNull String[] args
                ) {
                    if (permission != null && !sender.hasPermission(permission)) {
                        sender.sendMessage(org.bukkit.ChatColor.RED + "You don't have permission to use this command.");
                        return true;
                    }
                    return handler.apply(sender, args);
                }

                public @Nullable List<String> onTabComplete(
                    @NotNull CommandSender sender,
                    @NotNull Command command,
                    @NotNull String alias,
                    @NotNull String[] args
                ) {
                    if (args.length == 1 && tabCompletions != null && !tabCompletions.isEmpty()) {
                        return tabCompletions
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(java.util.stream.Collectors.toList());
                    }
                    return Collections.emptyList();
                }
            };
            pluginCommand.setExecutor(wrapper);
            pluginCommand.setTabCompleter(wrapper);
            plugin.getLogger().info("Registered legacy standalone command: " + commandName);
        }
    }

    private static CommandMap commandMap = null;

    private static @Nullable CommandMap getCommandMap() {
        if (commandMap != null) return commandMap;
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            return commandMap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a snapshot of currently registered dynamic command names.
     * Used to detect command registration changes during reload.
     */
    public static Set<String> getRegisteredDynamicCommandsSnapshot() {
        synchronized (registrationLock) {
            return new java.util.HashSet<>(registeredDynamicCommands);
        }
    }

    /**
     * Unregister all dynamically registered commands.
     * Should be called before reload to clean up old commands.
     */
    public static void unregisterAllDynamicCommands(GriefPrevention plugin) {
        synchronized (registrationLock) {
            CommandMap map = getCommandMap();
            if (map == null) {
                plugin.getLogger().warning("Could not get CommandMap for command unregistration");
                return;
            }

            try {
                // Get the knownCommands map from SimpleCommandMap
                Field knownCommandsField = map.getClass().getSuperclass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(map);

                for (String cmdName : registeredDynamicCommands) {
                    // Remove both the plain name and the prefixed version
                    knownCommands.remove(cmdName.toLowerCase());
                    knownCommands.remove(plugin.getName().toLowerCase() + ":" + cmdName.toLowerCase());
                    plugin.getLogger().info("Unregistered dynamic command: " + cmdName);
                }

                registeredDynamicCommands.clear();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to unregister dynamic commands: " + e.getMessage());
            }
        }
    }

    /**
     * Dynamically register the root command with a custom name from alias.yml.
     * This allows users to rename the main command (e.g., "claim" -> "terreno").
     */
    private void registerDynamicRootCommand(@NotNull String commandName) {
        synchronized (registrationLock) {
            try {
                CommandMap map = getCommandMap();
                if (map == null) {
                    plugin.getLogger().warning("Failed to get CommandMap for dynamic root command registration");
                    return;
                }

                // Create a dynamic root command that delegates to this handler
                DynamicRootCommand dynamicCommand = new DynamicRootCommand(
                    commandName,
                    rootCommandConfig != null ? rootCommandConfig.getDescription() : "GriefPrevention command",
                    "/" + commandName,
                    rootCommandConfig != null ? rootCommandConfig.getPermission() : "griefprevention.claims"
                );

                // Register using reflection to ensure we can override existing commands
                try {
                    Field knownCommandsField = map.getClass().getSuperclass().getDeclaredField("knownCommands");
                    knownCommandsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(map);

                    String lowerName = commandName.toLowerCase();
                    knownCommands.put(lowerName, dynamicCommand);
                    knownCommands.put(plugin.getName().toLowerCase() + ":" + lowerName, dynamicCommand);
                } catch (Exception e) {
                    // Fallback to normal registration
                    map.register(plugin.getName().toLowerCase(), dynamicCommand);
                }

                registeredDynamicCommands.add(commandName);
                plugin
                    .getLogger()
                    .info("Registered dynamic root command: " + commandName + " (alias for " + canonicalCommand + ")");
            } catch (Exception e) {
                plugin
                    .getLogger()
                    .warning("Failed to dynamically register root command '" + commandName + "': " + e.getMessage());
            }
        }
    }

    /**
     * Dynamic root command that delegates to this handler
     */
    private class DynamicRootCommand extends BukkitCommand {

        protected DynamicRootCommand(
            @NotNull String name,
            @Nullable String description,
            @Nullable String usageMessage,
            @Nullable String permission
        ) {
            super(name);
            if (description != null) setDescription(description);
            if (usageMessage != null) setUsage(usageMessage);
            if (permission != null) setPermission(permission);

            // Set aliases from config (excluding the primary name we're registering as)
            if (rootCommandConfig != null) {
                List<String> aliases = new ArrayList<>();
                for (String alias : rootCommandConfig.getCommands()) {
                    if (alias != null && !alias.equalsIgnoreCase(name) && !alias.equalsIgnoreCase(canonicalCommand)) {
                        aliases.add(alias.trim());
                    }
                }
                // Also add the canonical name as an alias so old command still works
                aliases.add(canonicalCommand);
                setAliases(aliases);
            }
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (getPermission() != null && !sender.hasPermission(getPermission())) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            // Delegate to the main handler
            return UnifiedCommandHandler.this.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args
        ) {
            List<String> result = UnifiedCommandHandler.this.onTabComplete(sender, this, alias, args);
            return result != null ? result : Collections.emptyList();
        }
    }

    /**
     * Dynamic command that can be registered at runtime without being in plugin.yml
     */
    private class DynamicStandaloneCommand extends BukkitCommand {

        private final BiFunction<CommandSender, String[], Boolean> handler;
        private final String subcommandName;

        protected DynamicStandaloneCommand(
            @NotNull String name,
            @Nullable String description,
            @Nullable String usageMessage,
            @Nullable String permission,
            @NotNull BiFunction<CommandSender, String[], Boolean> handler,
            @NotNull String subcommandName
        ) {
            super(name);
            this.handler = handler;
            this.subcommandName = subcommandName;
            if (description != null) setDescription(description);
            if (usageMessage != null) setUsage(usageMessage);
            if (permission != null) setPermission(permission);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (getPermission() != null && !sender.hasPermission(getPermission())) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            return handler.apply(sender, args);
        }

        @Override
        public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args
        ) {
            // For standalone commands, directly complete arguments for the specific subcommand
            // instead of treating it as a root command with subcommand selection
            String canonical = subcommandName; // subcommandName is already canonical
            Object handlerObj = subcommands.get(canonical);
            if (handlerObj instanceof TabExecutor) {
                // Create a mock command for TabExecutor
                org.bukkit.command.Command mockCommand = new org.bukkit.command.Command(canonical) {
                    @Override
                    public boolean execute(
                        @NotNull org.bukkit.command.CommandSender sender,
                        @NotNull String commandLabel,
                        @NotNull String[] args
                    ) {
                        return false;
                    }
                };
                return ((TabExecutor) handlerObj).onTabComplete(sender, mockCommand, alias, args);
            } else if (handlerObj != null) {
                // Use the subcommand argument completion logic
                return completeSubcommandArguments(sender, this, alias, canonical, args, args.length - 1);
            }
            return Collections.emptyList();
        }
    }

    /**
     * Register a standalone command from the Alias enum with custom TabExecutor
     */
    protected void registerStandaloneCommand(@NotNull Alias alias, @NotNull TabExecutor tabExecutor) {
        String standaloneName = alias.getStandalone();
        if (standaloneName == null || standaloneName.isEmpty()) return;

        PluginCommand pluginCommand = plugin.getCommand(standaloneName);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(tabExecutor);
            pluginCommand.setTabCompleter(tabExecutor);
            plugin.getLogger().info("Registered standalone command with TabExecutor: " + standaloneName);
        }
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!rootCommandEnabled) {
            plugin
                .getLogger()
                .info(
                    "Command '" + this.canonicalCommand + "' is disabled, blocking execution for " + sender.getName()
                );
            sender.sendMessage("This command is disabled.");
            return true;
        }

        if (args.length == 0) {
            handleDefault(sender);
            return true;
        }

        String providedSubcommand = args[0];
        String canonicalSubcommand = resolveCanonicalSubcommandName(providedSubcommand);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        if (canonicalSubcommand != null) {
            Object handlerObj = subcommands.get(canonicalSubcommand);
            if (handlerObj != null) {
                String[] translated = translateArguments(canonicalSubcommand, subArgs);
                boolean handled;
                if (handlerObj instanceof TabExecutor) {
                    // Create a mock command for TabExecutor
                    org.bukkit.command.Command mockCommand = new org.bukkit.command.Command(canonicalSubcommand) {
                        @Override
                        public boolean execute(
                            @NotNull org.bukkit.command.CommandSender sender,
                            @NotNull String commandLabel,
                            @NotNull String[] args
                        ) {
                            return false;
                        }
                    };
                    handled = ((TabExecutor) handlerObj).onCommand(
                        sender,
                        mockCommand,
                        canonicalSubcommand,
                        translated
                    );
                } else if (handlerObj instanceof BiFunction) {
                    @SuppressWarnings("unchecked")
                    BiFunction<CommandSender, String[], Boolean> biFunctionHandler = (BiFunction<
                        CommandSender,
                        String[],
                        Boolean
                    >) handlerObj;
                    handled = biFunctionHandler.apply(sender, translated);
                } else {
                    // Unknown handler type, skip
                    return false;
                }
                if (!handled) {
                    sendUsageMessage(sender, canonicalSubcommand);
                }
                return true;
            }
        }

        return handleUnknownSubcommand(sender, providedSubcommand.toLowerCase(Locale.ROOT), subArgs);
    }

    /**
     * Handle the command when no subcommand is provided
     */
    protected void handleDefault(CommandSender sender) {
        sendHelpMessage(sender, new String[0]);
    }

    /**
     * Handle unknown subcommands
     */
    protected abstract boolean handleUnknownSubcommand(CommandSender sender, String subcommand, String[] args);

    /**
     * Register a subcommand handler
     */
    protected void registerSubcommand(
        String name,
        BiFunction<CommandSender, String[], Boolean> handler,
        String... defaultAliases
    ) {
        String canonical = name.toLowerCase(Locale.ROOT);
        CommandAliasConfiguration.Subcommand config =
            rootCommandConfig != null ? rootCommandConfig.getSubcommand(canonical) : null;
        if (config != null && !config.isEnabled()) {
            return;
        }

        subcommands.put(canonical, handler);
        subcommandConfigs.put(canonical, config);

        // Always map canonical to itself for execution
        aliasToCanonical.put(canonical, canonical);

        // Register aliases from config (e.g., commands: [list2] means "list2" maps to canonical "list")
        // If config aliases exist, use those for tab completion; otherwise use canonical name
        if (config != null && !config.getCommands().isEmpty()) {
            for (String configAlias : config.getCommands()) {
                String lowerAlias = configAlias.toLowerCase(Locale.ROOT).trim();
                aliasToCanonical.put(lowerAlias, canonical);
                tabCompletionSuggestions.add(lowerAlias);
            }
        } else {
            // No config aliases, use canonical name for tab completion
            tabCompletionSuggestions.add(canonical);
        }

        // Register default aliases (for execution, not tab completion by default)
        for (String alias : defaultAliases) {
            registerAlias(canonical, alias);
        }
    }

    /**
     * Register a subcommand handler with custom tab completion
     */
    protected void registerSubcommand(String name, TabExecutor tabExecutor, String... defaultAliases) {
        String canonical = name.toLowerCase(Locale.ROOT);
        CommandAliasConfiguration.Subcommand config =
            rootCommandConfig != null ? rootCommandConfig.getSubcommand(canonical) : null;
        if (config != null && !config.isEnabled()) {
            return;
        }

        // Store the TabExecutor as both a handler and for tab completion
        subcommands.put(canonical, tabExecutor);
        subcommandConfigs.put(canonical, config);

        // Always map canonical to itself for execution
        aliasToCanonical.put(canonical, canonical);

        // Register aliases from config (e.g., commands: [list2] means "list2" maps to canonical "list")
        // If config aliases exist, use those for tab completion; otherwise use canonical name
        if (config != null && !config.getCommands().isEmpty()) {
            for (String configAlias : config.getCommands()) {
                String lowerAlias = configAlias.toLowerCase(Locale.ROOT).trim();
                aliasToCanonical.put(lowerAlias, canonical);
                tabCompletionSuggestions.add(lowerAlias);
            }
        } else {
            // No config aliases, use canonical name for tab completion
            tabCompletionSuggestions.add(canonical);
        }

        // Register default aliases (for execution, not tab completion by default)
        for (String alias : defaultAliases) {
            registerAlias(canonical, alias);
        }
    }

    /**
     * Register aliases for a subcommand
     */
    protected void registerAlias(String subcommand, String... aliases) {
        String canonical = subcommand.toLowerCase(Locale.ROOT);
        List<String> aliasList = subcommandAliases.computeIfAbsent(canonical, k -> new ArrayList<>());

        aliasToCanonical.put(canonical, canonical);

        if (aliases == null) {
            return;
        }

        for (String alias : aliases) {
            if (alias == null) continue;
            String lowerAlias = alias.toLowerCase(Locale.ROOT).trim();
            if (lowerAlias.isEmpty() || lowerAlias.equals(canonical)) continue;
            if (!aliasList.contains(lowerAlias)) {
                aliasList.add(lowerAlias);
            }
            aliasToCanonical.put(lowerAlias, canonical);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            // Suggest subcommands that start with the partial input
            // Use tabCompletionSuggestions which contains only the configured aliases
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return tabCompletionSuggestions
                .stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(java.util.stream.Collectors.toList());
        } else if (args.length >= 2) {
            // User has typed a space after the subcommand, now complete arguments
            String subcommand = args[0].toLowerCase();
            String canonical = resolveCanonicalSubcommandName(subcommand);
            Object handlerObj = canonical != null ? subcommands.get(canonical) : null;

            if (handlerObj instanceof TabExecutor) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                // Create a mock command for TabExecutor
                org.bukkit.command.Command mockCommand = new org.bukkit.command.Command(canonical) {
                    @Override
                    public boolean execute(
                        @NotNull org.bukkit.command.CommandSender sender,
                        @NotNull String commandLabel,
                        @NotNull String[] args
                    ) {
                        return false;
                    }
                };
                return ((TabExecutor) handlerObj).onTabComplete(sender, mockCommand, alias, subArgs);
            } else if (canonical != null) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                return completeSubcommandArguments(sender, command, alias, canonical, subArgs, subArgs.length - 1);
            }
        }

        return Collections.emptyList();
    }

    private void applyRootCommandMetadata(@NotNull PluginCommand pluginCommand) {
        if (rootCommandConfig == null) {
            return;
        }

        List<String> aliases = new ArrayList<>();
        for (String alias : rootCommandConfig.getCommands()) {
            if (alias == null) continue;
            String trimmed = alias.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.equalsIgnoreCase(canonicalCommand)) {
                aliases.add(trimmed);
            }
        }
        if (!aliases.isEmpty()) {
            pluginCommand.setAliases(aliases);
        }

        String description = rootCommandConfig.getDescription();
        if (description != null && !description.isBlank()) {
            pluginCommand.setDescription(description);
        }

        String permission = rootCommandConfig.getPermission();
        if (permission != null && !permission.isBlank()) {
            pluginCommand.setPermission(permission);
        }
    }

    private @Nullable String resolveCanonicalSubcommandName(@NotNull String name) {
        return aliasToCanonical.get(name.toLowerCase(Locale.ROOT));
    }

    private String[] translateArguments(@NotNull String canonical, String[] args) {
        CommandAliasConfiguration.Subcommand config = subcommandConfigs.get(canonical);
        if (config == null) {
            return args;
        }
        return config.translate(args);
    }

    protected void sendUsageMessage(@NotNull CommandSender sender, @NotNull String canonical) {
        String usage = getUsageText(canonical);
        if (usage == null || usage.isBlank()) {
            return;
        }

        sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + usage);

        String description = rootCommandConfig != null ? rootCommandConfig.getDescription() : null;
        if (description != null && !description.isBlank()) {
            sender.sendMessage(ChatColor.GRAY + description);
        }
    }

    protected void sendHelpMessage(@NotNull CommandSender sender, String[] args) {
        // Parse page number from arguments
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // Keep default page 1 if invalid number
            }
        }

        // Get all enabled subcommands
        List<Map.Entry<String, CommandAliasConfiguration.Subcommand>> entries = subcommandConfigs
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
            .sorted(Map.Entry.comparingByKey())
            .collect(java.util.stream.Collectors.toList());

        int commandsPerPage = 8;
        int totalPages = (int) Math.ceil((double) entries.size() / commandsPerPage);
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * commandsPerPage;
        int end = Math.min(start + commandsPerPage, entries.size());

        // Send header
        Messages headerMessage = "aclaim".equalsIgnoreCase(getPrimaryRootAlias())
            ? Messages.AClaimHelpHeader
            : Messages.ClaimHelpHeader;
        Messages legendMessage = "aclaim".equalsIgnoreCase(getPrimaryRootAlias())
            ? Messages.AClaimHelpLegend
            : Messages.ClaimHelpLegend;

        // Send header and legend messages
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, headerMessage);
            GriefPrevention.sendMessage(player, TextMode.Info, "");
            GriefPrevention.sendMessage(player, TextMode.Info, legendMessage);
            GriefPrevention.sendMessage(player, TextMode.Info, "");
        } else {
            sender.sendMessage(
                ChatColor.GOLD +
                    "Available /" +
                    getPrimaryRootAlias() +
                    " commands (Page " +
                    page +
                    "/" +
                    totalPages +
                    "):"
            );
            sender.sendMessage("");
            String legendText = GriefPrevention.instance.dataStore.getMessage(legendMessage, "");
            String[] legendParts = (legendText != null ? legendText : "").split(" - ", 2);
            if (legendParts.length == 2) {
                sender.sendMessage(legendParts[0].trim());
                sender.sendMessage(ChatColor.GRAY + " - " + legendParts[1].trim());
                sender.sendMessage("");
            }
        }

        // Show commands for current page
        if (start >= entries.size()) {
            if (sender instanceof Player) {
                GriefPrevention.sendMessage((Player) sender, TextMode.Err, "No commands found for page " + page);
            } else {
                sender.sendMessage(ChatColor.RED + "No commands found for page " + page);
            }
            return;
        }

        for (int i = start; i < end; i++) {
            Map.Entry<String, CommandAliasConfiguration.Subcommand> entry = entries.get(i);
            String canonical = entry.getKey();
            CommandAliasConfiguration.Subcommand config = entry.getValue();

            String subcommandUsage = getUsageText(canonical);
            if (subcommandUsage == null || subcommandUsage.isBlank()) {
                continue;
            }

            // Build the command line with proper formatting
            String[] parts = subcommandUsage.split(" - ", 2);
            StringBuilder line = new StringBuilder(ChatColor.YELLOW.toString()).append(parts[0].trim());

            if (parts.length > 1) {
                line.append(ChatColor.GRAY).append(" - ").append(parts[1].trim());
            } else if (config != null && config.getDescription() != null) {
                line.append(ChatColor.GRAY).append(" - ").append(config.getDescription());
            }

            sender.sendMessage(line.toString());
        }

        // Show pagination footer
        if (totalPages > 1) {
            sender.sendMessage("");
            if (page < totalPages) {
                Messages paginationMsg = this.canonicalCommand.equalsIgnoreCase("aclaim")
                    ? Messages.AClaimHelpPagination
                    : Messages.ClaimHelpPagination;
                sender.sendMessage(plugin.dataStore.getMessage(paginationMsg, String.valueOf(page + 1)));
            }
            if (page > 1) {
                sender.sendMessage(
                    ChatColor.GRAY + "Type /" + getPrimaryRootAlias() + " " + (page - 1) + " for the previous page."
                );
            }
        }
    }

    protected @NotNull String getPrimaryRootAlias() {
        if (rootCommandConfig != null && !rootCommandConfig.getCommands().isEmpty()) {
            return rootCommandConfig.getCommands().get(0);
        }
        return canonicalCommand;
    }

    private @Nullable String getUsageText(@NotNull String canonical) {
        CommandAliasConfiguration.Subcommand config = subcommandConfigs.get(canonical);
        if (config != null) {
            String usage = config.getUsage();
            if (usage != null && !usage.isBlank()) {
                return formatUsageText(usage);
            }

            String primaryAlias = !config.getCommands().isEmpty() ? config.getCommands().get(0) : canonical;
            return formatUsageText("/" + getPrimaryRootAlias() + " " + primaryAlias);
        }
        return null;
    }

    private @NotNull String formatUsageText(@NotNull String usage) {
        // Split into command/description parts if it contains a description
        String[] parts = usage.split(" - ", 2);
        String commandPart = parts[0].trim();
        String description = parts.length > 1 ? parts[1].trim() : "";

        // Get the default color from the command part (first color code, or yellow if
        // none)
        String defaultColor = ChatColor.YELLOW.toString();
        if (commandPart.startsWith("&")) {
            defaultColor = "&" + commandPart.charAt(1);
            commandPart = commandPart.substring(2);
        }

        // Color required parameters in red
        commandPart = commandPart.replaceAll("<([^>]+)>", ChatColor.RED + "<$1>" + defaultColor);

        // Rebuild the usage string with formatting
        String result = defaultColor + commandPart;
        if (!description.isEmpty()) {
            result += ChatColor.GRAY + " - " + ChatColor.WHITE + description;
        }

        return result;
    }

    private @NotNull List<String> completeSubcommandArguments(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String canonical,
        @NotNull String[] subArgs,
        int argumentIndex
    ) {
        CommandAliasConfiguration.Subcommand config = subcommandConfigs.get(canonical);
        if (config == null) {
            return Collections.emptyList();
        }

        // If we've provided all arguments for this subcommand and the current
        // input is not empty, don't suggest anything more
        if (subArgs.length > config.getArguments().size()) {
            return Collections.emptyList();
        }

        if (argumentIndex < 0 || argumentIndex >= config.getArguments().size()) {
            return Collections.emptyList();
        }

        CommandAliasConfiguration.Subcommand.Argument argument = config.getArgument(argumentIndex);
        if (argument == null) {
            return Collections.emptyList();
        }

        String current = subArgs.length > argumentIndex ? subArgs[argumentIndex] : "";
        String lowerPrefix = current.toLowerCase(Locale.ROOT);

        TreeSet<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        String type = argument.type();
        if (type != null) {
            // Check if it's a literal string (enclosed in single quotes)
            if (type.startsWith("'") && type.endsWith("'")) {
                String literal = type.substring(1, type.length() - 1);
                if (!literal.isEmpty()) {
                    suggestions.add(literal);
                }
            } else {
                switch (type) {
                    case "player", "online-player" -> {
                        List<String> players = TabCompletions.visiblePlayers(sender, new String[] { current });
                        // Exclude the sender themselves from player completions
                        if (sender instanceof Player playerSender) {
                            players.removeIf(name -> name.equalsIgnoreCase(playerSender.getName()));
                        }
                        suggestions.addAll(players);
                    }
                    case "integer" -> suggestions.addAll(TabCompletions.integer(new String[] { current }, 6, false));
                    case "integer-negative" -> suggestions.addAll(
                        TabCompletions.integer(new String[] { current }, 6, true)
                    );
                    default -> {
                        // no special handling
                    }
                }
            }
        }

        // Add option suggestions from the argument configuration
        for (String suggestion : argument.suggestions()) {
            if (suggestion == null) continue;
            suggestions.add(suggestion);
        }

        return suggestions
            .stream()
            .filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Resolve an option alias to its canonical name based on the subcommand configuration.
     * Uses the argumentAliases map which stores alias -> canonical mappings.
     *
     * @param subcommandName The subcommand name (e.g., "blocks")
     * @param argumentName   The argument name (unused, kept for API compatibility)
     * @param inputValue     The input value to resolve (e.g., "adjust")
     * @return The canonical option name, or the input value if no alias found
     */
    protected @NotNull String resolveOptionAlias(
        @NotNull String subcommandName,
        @NotNull String argumentName,
        @NotNull String inputValue
    ) {
        CommandAliasConfiguration.Subcommand config = subcommandConfigs.get(subcommandName.toLowerCase(Locale.ROOT));
        if (config == null) {
            return inputValue;
        }

        // Use the translate method which uses argumentAliases to resolve aliases
        String[] translated = config.translate(new String[] { inputValue });
        return translated.length > 0 ? translated[0] : inputValue;
    }
}
