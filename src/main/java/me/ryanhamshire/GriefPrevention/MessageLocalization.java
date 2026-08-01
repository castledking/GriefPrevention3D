package me.ryanhamshire.GriefPrevention;

import com.griefprevention.compat.Compat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public final class MessageLocalization
{

    private static final String DEFAULT_LOCALE = "en";

    private static final String[] BUNDLED_LOCALE_FILES = {"messages_en.yml", "messages_es.yml", "messages_pt_BR.yml", "messages_fr_FR.yml", "messages_de_DE.yml", "messages_ru_RU.yml", "messages_zh_CN.yml", "messages_ja_JP.yml", "messages_ms_MY.yml", "messages_pl_PL.yml", "messages_tr_TR.yml", "messages_uk_UA.yml", "messages_en_PT.yml"};
    static final String[] SUPPORTED_LOCALE_CODES = {"en", "es", "pt_BR", "fr_FR", "de_DE", "ru_RU", "zh_CN", "ja_JP", "ms_MY", "pl_PL", "tr_TR", "uk_UA", "en_PT"};

    private MessageLocalization()
    {
        throw new AssertionError("Instantiation of an utility class.");
    }

    public static void loadMessages(@NotNull String[] messages)
    {
        Messages[] messageIDs = Messages.values();

        extractLocaleFiles();

        String locale = DEFAULT_LOCALE;
        if (GriefPrevention.instance != null && GriefPrevention.instance.config_locale != null)
        {
            locale = GriefPrevention.instance.config_locale;
        }
        String originalLocale = locale;

        File messagesFile = new File(DataStore.messagesFilePath);
        File dataFolder = new File(DataStore.languageFolderPath);
        File localeFile = new File(dataFolder, "messages_" + locale + ".yml");

        File activeFile;
        String source;

        if (localeFile.exists())
        {
            activeFile = localeFile;
            source = "messages_" + locale + ".yml";
        }
        else if (messagesFile.exists())
        {
            activeFile = messagesFile;
            source = "messages.yml";
        }
        else
        {
            File detectedFile = null;
            String detectedLocale = null;
            if (dataFolder.exists() && dataFolder.isDirectory())
            {
                File[] langFiles = dataFolder.listFiles((dir, name) ->
                        name.startsWith("messages_") && name.endsWith(".yml"));
                if (langFiles != null && langFiles.length > 0)
                {
                    java.util.Arrays.sort(langFiles);
                    for (File f : langFiles)
                    {
                        String name = f.getName();
                        String code = name.substring("messages_".length(), name.length() - ".yml".length());
                        if (!code.isEmpty())
                        {
                            detectedFile = f;
                            detectedLocale = code;
                            break;
                        }
                    }
                }
            }

            if (detectedFile != null)
            {
                activeFile = detectedFile;
                source = detectedFile.getName();
                locale = detectedLocale;
                if (GriefPrevention.instance != null)
                {
                    GriefPrevention.instance.config_locale = locale;
                }
                GriefPrevention.AddLogEntry(
                        "Locale '" + originalLocale + "' didn't match provided " + source + ". Auto-switched to '" + detectedLocale + "'",
                        CustomLogEntryTypes.Debug, false);
            }
            else
            {
                if (!dataFolder.exists())
                {
                    dataFolder.mkdirs();
                }
                activeFile = null;
                source = null;
                File targetFile = new File(dataFolder, "messages_" + locale + ".yml");
                try (java.io.InputStream in = GriefPrevention.instance.getResource("messages_" + locale + ".yml"))
                {
                    if (in != null)
                    {
                        java.nio.file.Files.copy(in, targetFile.toPath());
                        activeFile = targetFile;
                        source = "messages_" + locale + ".yml";
                        GriefPrevention.AddLogEntry("Extracted " + source + " to " + DataStore.languageFolderPath,
                                CustomLogEntryTypes.Debug, false);
                    }
                    else
                    {
                        File enTarget = new File(dataFolder, "messages_en.yml");
                        try (java.io.InputStream enIn = GriefPrevention.instance.getResource("messages_en.yml"))
                        {
                            if (enIn != null)
                            {
                                java.nio.file.Files.copy(enIn, enTarget.toPath());
                                activeFile = enTarget;
                                source = "messages_en.yml";
                                locale = DEFAULT_LOCALE;
                                if (GriefPrevention.instance != null)
                                {
                                    GriefPrevention.instance.config_locale = locale;
                                }
                                GriefPrevention.AddLogEntry("Extracted messages_en.yml to " + DataStore.languageFolderPath,
                                        CustomLogEntryTypes.Debug, false);
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    GriefPrevention.AddLogEntry("Failed to extract messages_" + locale + ".yml: " + e.getMessage(),
                            CustomLogEntryTypes.Debug, false);
                }
            }
        }

        MessageFile config;
        if (activeFile != null)
        {
            config = MessageFile.load(activeFile);

            MessageFile bundledConfig = loadBundledResource(source);
            if (bundledConfig != null)
            {
                for (String key : bundledConfig.keys())
                {
                    if (!config.contains(key))
                    {
                        config.set(key, bundledConfig.getString(key, ""), notesFor(key));
                    }
                }
                if (config.hasPendingAdditions())
                {
                    try
                    {
                        config.save(activeFile);
                        GriefPrevention.AddLogEntry("Merged missing keys from bundled " + source,
                                CustomLogEntryTypes.Debug, false);
                    }
                    catch (IOException e)
                    {
                        GriefPrevention.AddLogEntry("Failed to save merged " + source + ": " + e.getMessage(),
                                CustomLogEntryTypes.Debug, false);
                    }
                }
            }
        }
        else
        {
            config = MessageFile.empty();
        }

        populateMessagesArray(messages, messageIDs, config);
    }

    // looks up the notes for a "Messages.SomeMessage" key, used as a comment when adding it to a file
    private static @Nullable String notesFor(@NotNull String key)
    {
        if (!key.startsWith("Messages.")) return null;

        String name = key.substring("Messages.".length());
        for (Messages message : Messages.values())
        {
            if (message.name().equals(name)) return message.notes;
        }
        return null;
    }

    // loads all supported locale files into the provided map for per-player message support
    public static void loadAllMessages(@NotNull java.util.Map<String, String[]> messagesByLocale, @NotNull String defaultLocale)
    {
        extractLocaleFiles();

        // Load the default locale first using existing logic (includes custom messages.yml overrides)
        String[] defaultMessages = new String[Messages.values().length];
        loadMessages(defaultMessages);
        messagesByLocale.put(defaultLocale, defaultMessages);

        // Load other bundled locales (without custom overrides)
        for (String localeCode : SUPPORTED_LOCALE_CODES)
        {
            if (localeCode.equals(defaultLocale)) continue;

            String[] messages = new String[Messages.values().length];
            loadSingleLocale(messages, localeCode);
            messagesByLocale.put(localeCode, messages);
        }

        // Ensure English is always available as ultimate fallback
        if (!messagesByLocale.containsKey("en"))
        {
            String[] enMessages = new String[Messages.values().length];
            loadSingleLocale(enMessages, "en");
            messagesByLocale.put("en", enMessages);
        }
    }

    // loads messages for a single locale from its data folder or bundled file
    private static void loadSingleLocale(@NotNull String[] messages, @NotNull String locale)
    {
        Messages[] messageIDs = Messages.values();
        File dataFolder = new File(DataStore.languageFolderPath);
        File localeFile = new File(dataFolder, "messages_" + locale + ".yml");

        MessageFile config;
        if (localeFile.exists())
        {
            config = MessageFile.load(localeFile);
        }
        else
        {
            config = loadBundledResource("messages_" + locale + ".yml");
        }

        if (config == null)
        {
            config = MessageFile.empty();
        }

        populateMessagesArray(messages, messageIDs, config);
    }

    // populates the messages array from a parsed message file
    private static void populateMessagesArray(@NotNull String[] messages, @NotNull Messages[] messageIDs, @NotNull MessageFile config)
    {
        for (Messages message : messageIDs)
        {
            String messagePath = "Messages." + message.name();
            if (config.contains(messagePath + ".Text"))
            {
                messages[message.ordinal()] = config.getString(messagePath + ".Text", message.defaultValue);
            }
            else
            {
                messages[message.ordinal()] = config.getString(messagePath, message.defaultValue);
            }

            if (message != Messages.HowToClaimRegex)
            {
                boolean hasUserColorCodes = messages[message.ordinal()].contains("$")
                        || messages[message.ordinal()].contains("&");
                boolean hasUserNewline = messages[message.ordinal()].contains("\\n");
                boolean isDisabledMessage = Compat.isBlank(messages[message.ordinal()]);

                if (!hasUserColorCodes && !isDisabledMessage)
                {
                    switch (message)
                    {
                        case ClaimHelpHeader:
                        case AClaimHelpHeader:
                            messages[message.ordinal()] = "&b&l" + messages[message.ordinal()];
                            break;
                        case ClaimHelpLegend:
                        case AClaimHelpLegend:
                            if (!hasUserNewline)
                            {
                                messages[message.ordinal()] = "\\n" + messages[message.ordinal()];
                            }
                            messages[message.ordinal()] = messages[message.ordinal()]
                                    .replace("<>", "&c<>")
                                    .replace("[]", "&a[]")
                                    .replace("-", "&7-");
                            break;
                        case ClaimHelpPagination:
                        case AClaimHelpPagination:
                            if (!hasUserNewline)
                            {
                                messages[message.ordinal()] = "\\n" + messages[message.ordinal()];
                            }
                            messages[message.ordinal()] = "&7" + messages[message.ordinal()];
                            break;
                        default:
                            break;
                    }
                }

                messages[message.ordinal()] = TextColor.translate(messages[message.ordinal()]);
                messages[message.ordinal()] = messages[message.ordinal()]
                        .replace("\\n", "\n");
            }

        }
    }

    // loads a message file bundled in the jar, returns null if not found
    private static @Nullable MessageFile loadBundledResource(@Nullable String fileName)
    {
        if (fileName == null) return null;

        try (java.io.InputStream in = GriefPrevention.instance.getResource(fileName))
        {
            if (in != null)
            {
                return MessageFile.load(in);
            }
        }
        catch (IOException e)
        {
        }
        return null;
    }

    public static void extractLocaleFiles()
    {
        File langFolder = new File(DataStore.dataLayerFolderPath + File.separator + "Lang");
        if (!langFolder.exists())
        {
            langFolder.mkdirs();
        }

        for (String fileName : BUNDLED_LOCALE_FILES)
        {
            File targetFile = new File(langFolder, fileName);
            if (!targetFile.exists())
            {
                try (java.io.InputStream in = GriefPrevention.instance.getResource(fileName))
                {
                    if (in != null)
                    {
                        java.nio.file.Files.copy(in, targetFile.toPath());
                    }
                }
                catch (IOException e)
                {
                    GriefPrevention.AddLogEntry("Failed to extract " + fileName + ": " + e.getMessage(),
                            CustomLogEntryTypes.Debug, false);
                }
            }
        }
    }

    private static @NotNull String normalizeLocale(@Nullable String locale)
    {
        if (locale == null || locale.trim().isEmpty()) return DEFAULT_LOCALE;
        return locale.trim().replace('-', '_');
    }

}
