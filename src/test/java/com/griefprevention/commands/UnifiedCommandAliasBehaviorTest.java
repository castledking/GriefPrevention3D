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
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            standalone: true
            commands:
              claim:
                enable: true
                commands: [claim]
                fallback: help
            """);
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        command.onCommand(sender, mock(Command.class), "claim", new String[0]);

        verify(sender, atLeastOnce()).sendMessage("CUSTOM claim create [radius] :: ");
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
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            standalone: true
            commands:
              claim:
                enable: true
                commands: [claim]
            """);
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        command.onCommand(sender, mock(Command.class), "claim", new String[] { "help" });

        verify(sender, atLeastOnce()).sendMessage("CUSTOM claim create [radius] :: ");
    }

    @Test
    void fallbackValueIsLoadedFromAliasConfiguration(@TempDir Path tempDir) throws Exception
    {
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            commands:
              claim:
                enable: true
                commands: [terreno]
                fallback: 'help 2'
            """);

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
        Files.writeString(file, yaml);

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
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        doAnswer(invocation -> {
            Messages message = invocation.getArgument(0);
            String[] args = invocation.getArgument(1);
            if (message == Messages.ClaimHelpEntry) {
                return "CUSTOM " + args[0].replaceAll("\u00A7.", "") + " :: " + args[1].replaceAll("\u00A7.", "");
            }
            if (message == Messages.ClaimHelpLegend || message == Messages.ClaimHelpPagination) {
                return "";
            }
            return message.name();
        }).when(dataStore).getMessage(org.mockito.ArgumentMatchers.any(Messages.class), org.mockito.ArgumentMatchers.any(String[].class));
        return plugin;
    }
}
