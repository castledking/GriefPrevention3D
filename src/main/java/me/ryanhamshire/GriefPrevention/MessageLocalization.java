package me.ryanhamshire.GriefPrevention;

import com.griefprevention.compat.Compat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class MessageLocalization
{

    private static final String DEFAULT_LOCALE = "en";

    private static final String[] BUNDLED_LOCALE_FILES = {"messages_en.yml", "messages_es.yml", "messages_pt_BR.yml"};

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

        FileConfiguration config;
        if (activeFile != null)
        {
            config = YamlConfiguration.loadConfiguration(activeFile);

            try (java.io.InputStream bundledIn = GriefPrevention.instance.getResource(source))
            {
                if (bundledIn != null)
                {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(bundledIn, StandardCharsets.UTF_8));
                    FileConfiguration bundledConfig = YamlConfiguration.loadConfiguration(reader);
                    boolean merged = false;
                    for (String key : bundledConfig.getKeys(true))
                    {
                        if (!config.contains(key))
                        {
                            config.set(key, bundledConfig.get(key));
                            merged = true;
                        }
                    }
                    if (merged)
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
            catch (IOException e)
            {
            }
        }
        else
        {
            config = new YamlConfiguration();
        }

        for (Messages message : messageIDs)
        {
            String messagePath = "Messages." + message.name();
            if (config.isString(messagePath + ".Text"))
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

            if (message.notes != null)
            {
                String notesString = config.getString(messagePath + ".Notes", message.notes);
                try
                {
                    List<String> notes = config.getComments(messagePath);
                    if (notes.isEmpty())
                    {
                        notes = Arrays.asList(notesString);
                    }
                    config.setComments(messagePath, notes);
                }
                catch (NoSuchMethodError e)
                {
                }
            }
        }

        if (messagesFile.exists())
        {
            try
            {
                try
                {
                    config.options().setHeader(Arrays.asList(
                            "Use a YAML editor like NotepadPlusPlus to edit this file.",
                            "After editing, back up your changes before reloading the server in case you made a syntax error.",
                            "Use dollar signs ($) for formatting codes, which are documented here: http://minecraft.wiki/Formatting_codes#Color_codes",
                            "Use \\n to create newlines in messages."));
                }
                catch (NoSuchMethodError e)
                {
                }
                config.save(DataStore.messagesFilePath);
            }
            catch (IOException exception)
            {
                Bukkit.getLogger()
                        .info("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
            }
        }
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
