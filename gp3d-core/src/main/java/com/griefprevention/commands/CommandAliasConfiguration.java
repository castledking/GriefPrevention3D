package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.Alias;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Platform-neutral representation of the configurable command and subcommand aliases
 * loaded from {@code alias.yml}.
 *
 * <p>This class replaces the Bukkit-specific {@code CommandAliasConfiguration} with
 * a pure-Java implementation using SnakeYAML directly.
 */
public final class CommandAliasConfiguration {

    private final boolean enabled;
    private final boolean standaloneEnabled;
    private final Map<String, RootCommand> rootCommands;

    private CommandAliasConfiguration(boolean enabled, boolean standaloneEnabled,
            @NotNull Map<String, RootCommand> rootCommands) {
        this.enabled = enabled;
        this.standaloneEnabled = standaloneEnabled;
        this.rootCommands = rootCommands;
    }

    /**
     * Returns whether the alias configuration system is globally enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns whether standalone commands (e.g. /trust, /trapped) are enabled.
     */
    public boolean isStandaloneEnabled() {
        return standaloneEnabled;
    }

    /**
     * Loads alias configuration from a file, merging with defaults.
     *
     * @param file the alias.yml file to load
     * @param logger logging consumer for warnings
     * @return the loaded configuration
     */
    public static @NotNull CommandAliasConfiguration load(@NotNull Path file, @NotNull Logger logger) {
        Map<String, Object> userConfig = loadYaml(file, logger);
        Map<String, Object> defaultConfig = loadDefaultYaml();
        Map<String, Object> merged = mergeConfigurations(defaultConfig, userConfig);

        boolean globalEnabled = getBoolean(merged, "enabled", true);
        boolean standaloneEnabled = getBoolean(merged, "standalone", true);

        Map<String, RootCommand> commands = new HashMap<>();
        Map<String, Object> commandSection = getMap(merged, "commands");
        if (commandSection != null) {
            for (Map.Entry<String, Object> entry : commandSection.entrySet()) {
                String key = entry.getKey();
                if (!(entry.getValue() instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                String normalizedKey = normalize(key);

                try {
                    boolean enabled = getBoolean(section, "enable", true);
                    List<String> aliases = getStringList(section, "commands");
                    String description = getString(section, "description");
                    String permission = getString(section, "permission");
                    boolean useAsHelpCmd = getBoolean(section, "use-as-help-cmd", false);
                    String fallback = getString(section, "fallback");

                    RootCommand rootCommand = new RootCommand(normalizedKey, enabled, aliases,
                            description, permission, useAsHelpCmd, fallback);
                    commands.put(normalizedKey, rootCommand);
                } catch (Exception e) {
                    logger.warning("Error loading command alias for " + key + " - using defaults");
                }
            }
        }

        Map<String, Object> subcommandSection = getMap(merged, "subcommands");
        if (subcommandSection != null) {
            for (Map.Entry<String, Object> rootEntry : subcommandSection.entrySet()) {
                String rootKey = rootEntry.getKey();
                if (!(rootEntry.getValue() instanceof Map)) continue;

                String normalizedRootKey = normalize(rootKey);
                RootCommand root = commands.computeIfAbsent(normalizedRootKey,
                        k -> new RootCommand(k, true, Collections.emptyList(), null, null, false, null));

                @SuppressWarnings("unchecked")
                Map<String, Object> subcommands = (Map<String, Object>) rootEntry.getValue();
                for (Map.Entry<String, Object> subEntry : subcommands.entrySet()) {
                    String subcommandKey = subEntry.getKey();
                    if (!(subEntry.getValue() instanceof Map)) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> subcommandEntry = (Map<String, Object>) subEntry.getValue();
                    String normalizedSubKey = normalize(subcommandKey);

                    boolean enabled = getBoolean(subcommandEntry, "enable", true);
                    List<String> aliases = getStringList(subcommandEntry, "commands");
                    List<String> standalone = getStringList(subcommandEntry, "standalone");
                    String description = getString(subcommandEntry, "description");
                    String permission = getString(subcommandEntry, "permission");
                    String usage = getString(subcommandEntry, "usage");

                    ArgumentParseResult argumentParseResult = parseArguments(
                            getMap(subcommandEntry, "arguments"));

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

        return new CommandAliasConfiguration(globalEnabled, standaloneEnabled, commands);
    }

    /**
     * Returns an empty configuration with default settings.
     */
    public static @NotNull CommandAliasConfiguration empty() {
        return new CommandAliasConfiguration(true, true, Collections.emptyMap());
    }

    public @Nullable RootCommand getRootCommand(@NotNull String key) {
        return rootCommands.get(normalize(key));
    }

    public @NotNull Map<String, RootCommand> getRootCommands() {
        return Collections.unmodifiableMap(rootCommands);
    }

    /**
     * Logger interface for platform-neutral logging.
     */
    public interface Logger {
        void info(@NotNull String message);
        void warning(@NotNull String message);
        void severe(@NotNull String message);
    }

    // --- YAML loading helpers ---

    private static @NotNull Map<String, Object> loadYaml(@NotNull Path file, @NotNull Logger logger) {
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) loaded;
                return coerceKeys(result);
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            logger.warning("Failed to load alias.yml, using defaults: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static @NotNull Map<String, Object> loadDefaultYaml() {
        try (InputStream in = CommandAliasConfiguration.class.getResourceAsStream("/alias.yml")) {
            if (in == null) {
                return Collections.emptyMap();
            }
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) loaded;
                return coerceKeys(result);
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default alias configuration", e);
        }
    }

    private static @NotNull Map<String, Object> mergeConfigurations(
            @NotNull Map<String, Object> defaults,
            @NotNull Map<String, Object> userConfig) {
        Map<String, Object> merged = new LinkedHashMap<>();
        copyValues(defaults, merged);
        copyValues(userConfig, merged);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void copyValues(@NotNull Map<String, Object> source, @NotNull Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> existing = target.containsKey(key)
                        ? (target.get(key) instanceof Map ? (Map<String, Object>) target.get(key) : new LinkedHashMap<>())
                        : new LinkedHashMap<>();
                copyValues((Map<String, Object>) value, existing);
                target.put(key, existing);
            } else {
                target.put(key, value);
            }
        }
    }

    // --- Utility methods ---

    /**
     * Recursively coerces all map keys to Strings.
     * SnakeYAML parses YAML boolean/null reserved words (true, false, on, off, yes, no)
     * as non-String types, which causes ClassCastExceptions downstream.
     */
    @SuppressWarnings("unchecked")
    private static @NotNull Map<String, Object> coerceKeys(@NotNull Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, coerceKeys((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, coerceList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull List<Object> coerceList(@NotNull List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(coerceKeys((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(coerceList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private static @Nullable Map<String, Object> getMap(@NotNull Map<String, Object> section, @NotNull String key) {
        Object value = section.get(key);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) value;
            return result;
        }
        return null;
    }

    private static @NotNull List<String> getStringList(@NotNull Map<String, Object> section, @NotNull String key) {
        Object value = section.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    String s = ((String) item).trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    private static @NotNull String getString(@NotNull Map<String, Object> section, @NotNull String key) {
        Object value = section.get(key);
        return value instanceof String ? (String) value : null;
    }

    private static boolean getBoolean(@NotNull Map<String, Object> section, @NotNull String key, boolean defaultValue) {
        Object value = section.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // --- Argument parsing ---

    private static @NotNull ArgumentParseResult parseArguments(@Nullable Map<String, Object> argumentsSection) {
        if (argumentsSection == null) {
            return new ArgumentParseResult(false, Collections.emptyMap(), Collections.emptyList());
        }

        Map<String, String> argumentAliases = new HashMap<>();
        List<Subcommand.Argument> arguments = new ArrayList<>();
        boolean translateArguments = false;

        for (Map.Entry<String, Object> entry : argumentsSection.entrySet()) {
            String argumentKey = entry.getKey();
            Object argumentValue = entry.getValue();
            String argumentType = null;
            LinkedHashSet<String> suggestions = new LinkedHashSet<>();

            if (argumentValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> argumentSection = (Map<String, Object>) argumentValue;

                String type = getString(argumentSection, "type");
                if (type != null) {
                    argumentType = type.trim().toLowerCase(Locale.ROOT);
                }

                Map<String, Object> optionsSection = getMap(argumentSection, "options");
                if (optionsSection != null) {
                    translateArguments = true;
                    for (Map.Entry<String, Object> optionEntry : optionsSection.entrySet()) {
                        String canonical = optionEntry.getKey().trim();
                        if (canonical.isEmpty()) continue;

                        argumentAliases.put(normalize(canonical), canonical);

                        List<String> aliasList = getAliasList(optionEntry.getValue());
                        if (aliasList.isEmpty()) {
                            suggestions.add(canonical);
                        } else {
                            for (String alias : aliasList) {
                                String normalizedAlias = normalize(alias);
                                if (!normalizedAlias.isEmpty()) {
                                    argumentAliases.put(normalizedAlias, canonical);
                                }
                                suggestions.add(alias);
                            }
                        }
                    }
                } else {
                    // Backwards compatibility: treat direct children as canonical values
                    for (Map.Entry<String, Object> optEntry : argumentSection.entrySet()) {
                        String canonicalValue = optEntry.getKey();
                        if (canonicalValue.equalsIgnoreCase("type")) continue;

                        String canonical = canonicalValue.trim();
                        if (canonical.isEmpty()) continue;

                        translateArguments = true;
                        argumentAliases.put(normalize(canonical), canonical);

                        List<String> aliasList = getAliasList(optEntry.getValue());
                        if (aliasList.isEmpty()) {
                            suggestions.add(canonical);
                        } else {
                            for (String alias : aliasList) {
                                String normalizedAlias = normalize(alias);
                                if (!normalizedAlias.isEmpty()) {
                                    argumentAliases.put(normalizedAlias, canonical);
                                }
                                suggestions.add(alias);
                            }
                        }
                    }
                }
            } else if (argumentValue instanceof String) {
                String value = ((String) argumentValue).trim();
                if (!value.isEmpty()) {
                    argumentType = value.toLowerCase(Locale.ROOT);
                }
            }

            arguments.add(new Subcommand.Argument(argumentKey, argumentType,
                    Collections.unmodifiableList(new ArrayList<>(suggestions))));
        }

        return new ArgumentParseResult(translateArguments, argumentAliases,
                Collections.unmodifiableList(arguments));
    }

    private static @NotNull List<String> getAliasList(@NotNull Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    String s = ((String) item).trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    // --- Inner classes ---

    public static final class RootCommand {
        private final String key;
        private final boolean enabled;
        private final List<String> commands;
        private final String description;
        private final String permission;
        private final boolean useAsHelpCmd;
        private final String fallback;
        private final Map<String, Subcommand> subcommands = new HashMap<>();

        private RootCommand(String key, boolean enabled, List<String> commands, String description,
                String permission, boolean useAsHelpCmd, String fallback) {
            this.key = key;
            this.enabled = enabled;
            this.commands = commands == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(commands));
            this.description = description;
            this.permission = permission;
            this.useAsHelpCmd = useAsHelpCmd;
            this.fallback = fallback;
        }

        public @NotNull String getKey() { return key; }
        public boolean isEnabled() { return enabled; }
        public @NotNull List<String> getCommands() { return commands; }
        public @Nullable String getDescription() { return description; }
        public @Nullable String getPermission() { return permission; }
        public boolean shouldUseAsHelpCmd() { return useAsHelpCmd; }
        public @Nullable String getFallback() { return fallback; }

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

        private Subcommand(String key, boolean enabled, List<String> commands, List<String> standalone,
                String description, String permission, String usage,
                boolean translateArguments, Map<String, String> argumentAliases,
                List<Argument> arguments) {
            this.key = key;
            this.enabled = enabled;
            this.commands = commands == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(commands));
            this.standalone = standalone == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(standalone));
            this.description = description;
            this.permission = permission;
            this.usage = usage;
            this.translateArguments = translateArguments;
            this.argumentAliases = argumentAliases == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new HashMap<>(argumentAliases));
            this.arguments = arguments == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(arguments));
        }

        public @NotNull String getKey() { return key; }
        public boolean isEnabled() { return enabled; }
        public @NotNull List<String> getCommands() { return commands; }
        public @NotNull List<String> getStandalone() { return standalone; }

        public boolean isEffectivelyEnabled() {
            return enabled && !standalone.isEmpty();
        }

        public @Nullable String getDescription() { return description; }
        public @Nullable String getPermission() { return permission; }
        public @Nullable String getUsage() { return usage; }
        public boolean shouldTranslateArguments() { return translateArguments; }

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

        public @NotNull List<Argument> getArguments() { return arguments; }

        public @Nullable Argument getArgument(int index) {
            if (index < 0 || index >= arguments.size()) return null;
            return arguments.get(index);
        }

        public static final class Argument {
            private final @NotNull String name;
            private final @Nullable String type;
            private final @NotNull List<String> suggestions;
            private final boolean optional;

            public Argument(@NotNull String name, @Nullable String type, @NotNull List<String> suggestions) {
                this(name, type, suggestions, false);
            }

            public Argument(@NotNull String name, @Nullable String type, @NotNull List<String> suggestions,
                    boolean optional) {
                this.name = name;
                this.type = type;
                this.suggestions = Collections.unmodifiableList(new ArrayList<>(suggestions));
                this.optional = optional;
            }

            public @NotNull String name() { return name; }
            public @Nullable String type() { return type; }
            public @NotNull List<String> suggestions() { return suggestions; }
            public boolean optional() { return optional; }
        }
    }

    private static final class ArgumentParseResult {
        private final boolean translateArguments;
        private final @NotNull Map<String, String> argumentAliases;
        private final @NotNull List<Subcommand.Argument> arguments;

        private ArgumentParseResult(boolean translateArguments,
                @NotNull Map<String, String> argumentAliases,
                @NotNull List<Subcommand.Argument> arguments) {
            this.translateArguments = translateArguments;
            this.argumentAliases = Collections.unmodifiableMap(new HashMap<>(argumentAliases));
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }

        boolean translateArguments() { return translateArguments; }
        @NotNull Map<String, String> argumentAliases() { return argumentAliases; }
        @NotNull List<Subcommand.Argument> arguments() { return arguments; }
    }
}
