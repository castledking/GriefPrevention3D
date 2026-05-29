package com.griefprevention.commands;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class UnifiedCommandAliasBehaviorTest
{
    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        PluginManager pluginManager = mock(PluginManager.class);
        doReturn(pluginManager).when(server).getPluginManager();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void rootCommandFallbackDispatchesConfiguredSubcommand(@TempDir Path tempDir) throws Exception
    {
        CommandAliasConfiguration aliases = loadAliases(tempDir,
                "enabled: true\n"
                        + "standalone: true\n"
                        + "commands:\n"
                        + "  claim:\n"
                        + "    enable: true\n"
                        + "    commands: [claim]\n"
                        + "    fallback: help\n"
                        + "subcommands:\n"
                        + "  claim:\n"
                        + "    create:\n"
                        + "      enable: true\n"
                        + "      commands: [create]\n"
                        + "      standalone: [createclaim]\n"
                        + "      usage: \"/claim create [radius]\"\n"
                        + "      description: Create or expand a claim centered on you.\n");
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        command.onCommand(sender, mock(Command.class), "claim", new String[0]);

        verify(sender, atLeastOnce()).sendMessage("CUSTOM claim create [radius] :: Create or expand a claim centered on you.");
        verify(plugin.dataStore, never()).createClaim(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void helpRowsUseConfiguredMessageTemplate(@TempDir Path tempDir) throws Exception
    {
        CommandAliasConfiguration aliases = loadAliases(tempDir,
                "enabled: true\n"
                        + "standalone: true\n"
                        + "commands:\n"
                        + "  claim:\n"
                        + "    enable: true\n"
                        + "    commands: [claim]\n"
                        + "subcommands:\n"
                        + "  claim:\n"
                        + "    create:\n"
                        + "      enable: true\n"
                        + "      commands: [create]\n"
                        + "      standalone: [createclaim]\n"
                        + "      usage: \"/claim create [radius]\"\n"
                        + "      description: Create or expand a claim centered on you.\n");
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        command.onCommand(sender, mock(Command.class), "claim", new String[] { "help" });

        // Let's see what messages are actually being sent
        verify(sender, atLeastOnce()).sendMessage("§6Available /claim commands (Page 1/1):");
        verify(sender, atLeastOnce()).sendMessage("");
        // The help entry should be sent but isn't
        verify(sender, atLeastOnce()).sendMessage("CUSTOM claim create [radius] :: Create or expand a claim centered on you.");
    }

    @Test
    void fallbackValueIsLoadedFromAliasConfiguration(@TempDir Path tempDir) throws Exception
    {
        CommandAliasConfiguration aliases = loadAliases(tempDir,
                "enabled: true\n"
                        + "commands:\n"
                        + "  claim:\n"
                        + "    enable: true\n"
                        + "    commands: [terreno]\n"
                        + "    fallback: 'help 2'\n");

        assertEquals("help 2", aliases.getRootCommand("claim").getFallback());
    }

    @Test
    void colorOnlyMessagesAreSuppressed()
    {
        Player sender = mock(Player.class);

        GriefPrevention.sendMessage(sender, TextMode.Info, "\u00A7b", 0);

        verify(sender, never()).sendMessage(anyString());
        assertTrue(!GriefPrevention.hasVisibleMessageContent(""));
        assertTrue(!GriefPrevention.hasVisibleMessageContent("\u00A7c\n"));
    }

    private static CommandAliasConfiguration loadAliases(Path tempDir, String yaml) throws Exception
    {
        Path file = tempDir.resolve("alias.yml");
        Files.write(file, yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        GriefPrevention plugin = mock(GriefPrevention.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        return CommandAliasConfiguration.load(plugin, file.toFile());
    }

    private static GriefPrevention newPlugin(CommandAliasConfiguration aliases)
    {
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        when(plugin.getCommandAliases()).thenReturn(aliases);
        when(plugin.getCommand(anyString())).thenReturn(mock(PluginCommand.class));
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);

        doAnswer(invocation -> {
            Messages message = invocation.getArgument(0);
            Object[] args = invocation.getArguments();
            // Handle varargs - skip the first argument (Messages enum) and process the rest as strings
            if (message == Messages.ClaimHelpEntry && args.length >= 3) {
                String command = args[1] != null ? args[1].toString().replaceAll("\u00A7.", "") : "";
                String description = args[2] != null ? args[2].toString().replaceAll("\u00A7.", "") : "";
                return "CUSTOM " + command + " :: " + description;
            }
            if (message == Messages.ClaimHelpHeader) {
                return "§6Available /claim commands (Page 1/1):";
            }
            if (message == Messages.ClaimHelpLegend || message == Messages.ClaimHelpPagination) {
                return "";
            }
            return message.name();
        }).when(dataStore).getMessage(org.mockito.ArgumentMatchers.any(Messages.class), org.mockito.ArgumentMatchers.any(String.class), org.mockito.ArgumentMatchers.any(String.class));

        return plugin;
    }
}
