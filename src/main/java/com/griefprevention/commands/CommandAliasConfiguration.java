package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Alias;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Represents the configurable command and subcommand aliases loaded from
 * {@code alias.yml}.
 */
public final class CommandAliasConfiguration {

    private final Map<String, RootCommand> rootCommands;

    private CommandAliasConfiguration(@NotNull Map<String, RootCommand> rootCommands) {
        this.rootCommands = rootCommands;
    }

    public static @NotNull CommandAliasConfiguration load(@NotNull GriefPrevention plugin, @NotNull File file) {
        YamlConfiguration configuration = new YamlConfiguration();

        // If file exists, try to load it
        if (file.exists()) {
            try {
                configuration = YamlConfiguration.loadConfiguration(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load alias.yml, using defaults", e);
                configuration = new YamlConfiguration();
            }
        }

        // Always load defaults
        YamlConfiguration defaultConfig = getDefaultConfiguration();

        // If the file is empty or invalid, use and save defaults
        if (configuration.getKeys(true).isEmpty()) {
            configuration = defaultConfig;
            try {
                // Ensure parent directory exists
                file.getParentFile().mkdirs();
                // Save the default config
                defaultConfig.save(file);
                plugin.getLogger().info("Created default alias.yml with built-in defaults");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save default alias configuration", e);
            }
        }

        // Merge user configuration with defaults, preserving user customizations
        YamlConfiguration mergedConfig = mergeConfigurations(defaultConfig, configuration);

        Map<String, RootCommand> commands = new HashMap<>();
        ConfigurationSection commandSection = mergedConfig.getConfigurationSection("commands");
        if (commandSection != null) {
            for (String key : commandSection.getKeys(false)) {
                String normalizedKey = normalize(key);
                ConfigurationSection entry = commandSection.getConfigurationSection(key);
                if (entry == null) {
                    plugin.getLogger().warning("Invalid command entry in alias.yml: " + key + " - using defaults");
                    continue;
                }

                try {
                    boolean enabled = entry.getBoolean("enable", true);
                    List<String> aliases = readStringList(entry, "commands");
                    String description = entry.getString("description");
                    String permission = entry.getString("permission");
                    boolean useAsHelpCmd = entry.getBoolean("use-as-help-cmd", false);

                    RootCommand rootCommand = new RootCommand(normalizedKey, enabled, aliases, description, permission,
                            useAsHelpCmd);
                    commands.put(normalizedKey, rootCommand);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Error loading command alias for " + key + " - using defaults", e);
                }
            }
        }

        ConfigurationSection subcommandSection = mergedConfig.getConfigurationSection("subcommands");
        if (subcommandSection != null) {
            for (String rootKey : subcommandSection.getKeys(false)) {
                String normalizedRootKey = normalize(rootKey);
                RootCommand root = commands.computeIfAbsent(normalizedRootKey,
                        k -> new RootCommand(k, true, List.of(), null, null, false));

                ConfigurationSection subcommands = subcommandSection.getConfigurationSection(rootKey);
                if (subcommands == null)
                    continue;

                for (String subcommandKey : subcommands.getKeys(false)) {
                    String normalizedSubKey = normalize(subcommandKey);
                    ConfigurationSection subcommandEntry = subcommands.getConfigurationSection(subcommandKey);
                    if (subcommandEntry == null)
                        continue;

                    boolean enabled = subcommandEntry.getBoolean("enable", true);
                    List<String> aliases = readStringList(subcommandEntry, "commands");
                    List<String> standalone = readStringList(subcommandEntry, "standalone");
                    String description = subcommandEntry.getString("description");
                    String permission = subcommandEntry.getString("permission");
                    String usage = subcommandEntry.getString("usage");

                    ArgumentParseResult argumentParseResult = parseArguments(
                            subcommandEntry.getConfigurationSection("arguments"));

                    Subcommand subcommand = new Subcommand(
                            normalizedSubKey,
                            enabled,
                            aliases,
                            standalone,
                            description,
                            permission,
                            usage,
                            argumentParseResult.translateArguments(),
                            argumentParseResult.argumentAliases(),
                            argumentParseResult.arguments());
                    root.subcommands.put(normalizedSubKey, subcommand);
                }
            }
        }

        return new CommandAliasConfiguration(commands);
    }

    public static @NotNull CommandAliasConfiguration empty() {
        return new CommandAliasConfiguration(Collections.emptyMap());
    }

    private static @NotNull YamlConfiguration getDefaultConfiguration() {
        YamlConfiguration defaultConfig = new YamlConfiguration();
        try {
            defaultConfig.loadFromString(Alias.getDefaultYaml());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default alias configuration", e);
        }
        return defaultConfig;
    }

    public @Nullable RootCommand getRootCommand(@NotNull String key) {
        RootCommand command = rootCommands.get(normalize(key));
        if (command != null) {
            // Check if this root command has any enabled subcommands
            boolean hasEnabledSubcommands = command.subcommands.values().stream()
                    .anyMatch(Subcommand::isEnabled);

            // If it's a root command with subcommands but none are enabled, return null
            if (!command.subcommands.isEmpty() && !hasEnabledSubcommands) {
                return null;
            }
        }
        return command;
    }

    public static @NotNull YamlConfiguration mergeConfigurations(@NotNull YamlConfiguration defaults,
            @NotNull YamlConfiguration userConfig) {
        YamlConfiguration merged = new YamlConfiguration();

        // First, handle the commands section
        ConfigurationSection defaultCommands = defaults.getConfigurationSection("commands");
        ConfigurationSection userCommands = userConfig.getConfigurationSection("commands");

        if (defaultCommands != null) {
            for (String commandKey : defaultCommands.getKeys(false)) {
                ConfigurationSection defaultCommand = defaultCommands.getConfigurationSection(commandKey);
                ConfigurationSection userCommand = userCommands != null
                        ? userCommands.getConfigurationSection(commandKey)
                        : null;

                // Create or get the command section in merged config
                String commandPath = "commands." + commandKey;
                ConfigurationSection mergedCommand = merged.createSection(commandPath);

                // Copy all default values first
                if (defaultCommand != null) {
                    for (String key : defaultCommand.getKeys(false)) {
                        mergedCommand.set(key, defaultCommand.get(key));
                    }
                }

                // Override with user values if they exist
                if (userCommand != null) {
                    for (String key : userCommand.getKeys(false)) {
                        mergedCommand.set(key, userCommand.get(key));
                    }
                }
            }
        }

        // Then handle the subcommands section similarly
        ConfigurationSection defaultSubcommands = defaults.getConfigurationSection("subcommands");
        ConfigurationSection userSubcommands = userConfig.getConfigurationSection("subcommands");

        if (defaultSubcommands != null) {
            for (String rootKey : defaultSubcommands.getKeys(false)) {
                ConfigurationSection defaultRoot = defaultSubcommands.getConfigurationSection(rootKey);
                ConfigurationSection userRoot = userSubcommands != null
                        ? userSubcommands.getConfigurationSection(rootKey)
                        : null;

                if (defaultRoot != null) {
                    for (String subKey : defaultRoot.getKeys(false)) {
                        ConfigurationSection defaultSub = defaultRoot.getConfigurationSection(subKey);
                        ConfigurationSection userSub = (userRoot != null) ? userRoot.getConfigurationSection(subKey)
                                : null;

                        String subPath = "subcommands." + rootKey + "." + subKey;
                        ConfigurationSection mergedSub = merged.createSection(subPath);

                        // Copy all default values first
                        if (defaultSub != null) {
                            for (String key : defaultSub.getKeys(false)) {
                                mergedSub.set(key, defaultSub.get(key));
                            }
                        }

                        // Override with user values if they exist
                        if (userSub != null) {
                            for (String key : userSub.getKeys(false)) {
                                mergedSub.set(key, userSub.get(key));
                            }
                        }
                    }
                }
            }
        }

        // Override with user configuration (preserves user customizations)
        for (String key : userConfig.getKeys(true)) {
            if (userConfig.isConfigurationSection(key)) {
                // Handle configuration sections
                ConfigurationSection userSection = userConfig.getConfigurationSection(key);
                if (userSection != null) {
                    copySection(userSection, merged, key);
                }
            } else {
                // Override primitive values
                Object value = userConfig.get(key);
                merged.set(key, value);
            }
        }

        return merged;
    }

    private static void copySection(@NotNull ConfigurationSection source, @NotNull YamlConfiguration target,
            @NotNull String path) {
        ConfigurationSection targetSection = target.createSection(path);

        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                ConfigurationSection subSection = (ConfigurationSection) value;
                copySection(subSection, target, path + "." + key);
            } else {
                targetSection.set(key, value);
            }
        }
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private static @NotNull List<String> readStringList(@NotNull ConfigurationSection section, @NotNull String path) {
        List<String> list = section.getStringList(path);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String entry : list) {
            if (entry == null || entry.isBlank())
                continue;
            filtered.add(entry.trim());
        }
        return filtered;
    }

    private static @NotNull ArgumentParseResult parseArguments(@Nullable ConfigurationSection argumentsSection) {
        if (argumentsSection == null) {
            return new ArgumentParseResult(false, Map.of(), List.of());
        }

        Map<String, String> argumentAliases = new HashMap<>();
        List<Subcommand.Argument> arguments = new ArrayList<>();
        boolean translateArguments = false;

        for (String argumentKey : argumentsSection.getKeys(false)) {
            ConfigurationSection argumentSection = argumentsSection.getConfigurationSection(argumentKey);
            String argumentName = argumentKey;
            String argumentType = null;
            LinkedHashSet<String> suggestions = new LinkedHashSet<>();

            if (argumentSection == null) {
                String value = argumentsSection.getString(argumentKey);
                if (value != null && !value.isBlank()) {
                    argumentType = value.trim().toLowerCase(Locale.ROOT);
                }
            } else {
                String type = argumentSection.getString("type");
                if (type != null && !type.isBlank()) {
                    argumentType = type.trim().toLowerCase(Locale.ROOT);
                }

                ConfigurationSection optionsSection = argumentSection.getConfigurationSection("options");
                if (optionsSection != null) {
                    translateArguments = true;
                    for (String canonicalValue : optionsSection.getKeys(false)) {
                        String canonical = canonicalValue.trim();
                        if (canonical.isEmpty())
                            continue;

                        argumentAliases.put(normalize(canonical), canonical);

                        List<String> aliasList = optionsSection.getStringList(canonicalValue);
                        if (aliasList.isEmpty()) {
                            // No aliases configured, show canonical name
                            suggestions.add(canonical);
                        } else {
                            // Aliases exist - only show aliases, not canonical name
                            for (String alias : aliasList) {
                                String normalizedAlias = normalize(alias);
                                if (normalizedAlias.isEmpty())
                                    continue;
                                argumentAliases.put(normalizedAlias, canonical);
                                suggestions.add(alias.trim());
                            }
                        }
                    }
                } else {
                    // Backwards compatibility: treat direct children as canonical values.
                    for (String canonicalValue : argumentSection.getKeys(false)) {
                        // Skip reserved field names
                        if (canonicalValue.equalsIgnoreCase("type")) {
                            continue;
                        }

                        String canonical = canonicalValue.trim();
                        if (canonical.isEmpty())
                            continue;

                        translateArguments = true;
                        argumentAliases.put(normalize(canonical), canonical);

                        List<String> aliasList = argumentSection.getStringList(canonicalValue);
                        if (aliasList.isEmpty()) {
                            // No aliases configured, show canonical name
                            suggestions.add(canonical);
                        } else {
                            // Aliases exist - only show aliases, not canonical name
                            for (String alias : aliasList) {
                                String normalizedAlias = normalize(alias);
                                if (normalizedAlias.isEmpty())
                                    continue;
                                argumentAliases.put(normalizedAlias, canonical);
                                suggestions.add(alias.trim());
                            }
                        }
                    }
                }
            }

            arguments.add(new Subcommand.Argument(argumentName, argumentType, List.copyOf(suggestions)));
        }

        return new ArgumentParseResult(translateArguments, argumentAliases, List.copyOf(arguments));
    }

    public static final class RootCommand {
        private final String key;
        private final boolean enabled;
        private final List<String> commands;
        private final String description;
        private final String permission;
        private final boolean useAsHelpCmd;
        private final Map<String, Subcommand> subcommands = new HashMap<>();

        private RootCommand(String key, boolean enabled, List<String> commands, String description, String permission,
                boolean useAsHelpCmd) {
            this.key = key;
            this.enabled = enabled;
            this.commands = commands == null ? List.of() : List.copyOf(commands);
            this.description = description;
            this.permission = permission;
            this.useAsHelpCmd = useAsHelpCmd;
        }

        public @NotNull String getKey() {
            return key;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public @NotNull List<String> getCommands() {
            return commands;
        }

        public @Nullable String getDescription() {
            return description;
        }

        public @Nullable String getPermission() {
            return permission;
        }

        public boolean shouldUseAsHelpCmd() {
            return useAsHelpCmd;
        }

        public @Nullable Subcommand getSubcommand(@NotNull String key) {
            return subcommands.get(normalize(key));
        }

        public @NotNull Map<String, Subcommand> getSubcommands() {
            return Collections.unmodifiableMap(subcommands);
        }
    }

    public static final class Subcommand {
        private final String key;
        private final boolean enabled;
        private final List<String> commands;
        private final List<String> standalone;
        private final String description;
        private final String permission;
        private final String usage;
        private final boolean translateArguments;
        private final Map<String, String> argumentAliases;
        private final List<Argument> arguments;

        private Subcommand(String key,
                boolean enabled,
                List<String> commands,
                List<String> standalone,
                String description,
                String permission,
                String usage,
                boolean translateArguments,
                Map<String, String> argumentAliases,
                List<Argument> arguments) {
            this.key = key;
            this.enabled = enabled;
            this.commands = commands == null ? List.of() : List.copyOf(commands);
            this.standalone = standalone == null ? List.of() : List.copyOf(standalone);
            this.description = description;
            this.permission = permission;
            this.usage = usage;
            this.translateArguments = translateArguments;
            this.argumentAliases = argumentAliases == null ? Map.of() : Map.copyOf(argumentAliases);
            this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }

        public @NotNull String getKey() {
            return key;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public @NotNull List<String> getCommands() {
            return commands;
        }

        public @NotNull List<String> getStandalone() {
            return standalone;
        }

        public boolean isEffectivelyEnabled() {
            // Command is disabled if enable=false OR if standalone array is explicitly empty
            return enabled && !standalone.isEmpty();
        }

        public @Nullable String getDescription() {
            return description;
        }

        public @Nullable String getPermission() {
            return permission;
        }

        public @Nullable String getUsage() {
            return usage;
        }

        public boolean shouldTranslateArguments() {
            return translateArguments;
        }

        public @NotNull String[] translate(@NotNull String[] args) {
            if (!translateArguments || argumentAliases.isEmpty() || args.length == 0) {
                return args;
            }

            String[] translated = args.clone();
            for (int i = 0; i < translated.length; i++) {
                String alias = argumentAliases.get(normalize(translated[i]));
                if (alias != null) {
                    translated[i] = alias;
                }
            }
            return translated;
        }

        public @NotNull List<Argument> getArguments() {
            return arguments;
        }

        public @Nullable Argument getArgument(int index) {
            if (index < 0 || index >= arguments.size())
                return null;
            return arguments.get(index);
        }

        public record Argument(@NotNull String name, @Nullable String type, @NotNull List<String> suggestions,
                boolean optional) {
            public Argument(@NotNull String name, @Nullable String type, @NotNull List<String> suggestions) {
                this(name, type, suggestions, false);
            }
        }
    }

    private record ArgumentParseResult(boolean translateArguments,
            @NotNull Map<String, String> argumentAliases,
            @NotNull List<Subcommand.Argument> arguments) {
    }
}
