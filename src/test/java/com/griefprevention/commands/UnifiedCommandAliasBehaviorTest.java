package com.griefprevention.commands;

import com.griefprevention.commands.CommandAliasConfiguration;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class UnifiedCommandAliasBehaviorTest {

    @Test
    void rootCommandUseAsHelpCmdShowsHelp(@TempDir Path tempDir) throws Exception {
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            commands:
              claim:
                enable: true
                commands: [claim]
                use-as-help-cmd: true
            subcommands:
              claim:
                create:
                  enable: true
                  commands: [create]
                  standalone: [createclaim]
                  usage: "/claim create [radius]"
                  description: Create or expand a claim centered on you.
            """);
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        command.onCommand(sender, mock(Command.class), "claim", new String[0]);

        // Verify the help message is sent with the correct format
        verify(sender, atLeastOnce()).sendMessage("§e§e/claim create [radius]§7 - Create or expand a claim centered on you.");
    }

    @Test
    void helpRowsUseConfiguredMessageTemplate(@TempDir Path tempDir) throws Exception {
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            commands:
              claim:
                enable: true
                commands: [claim]
            subcommands:
              claim:
                create:
                  enable: true
                  commands: [create]
                  standalone: [createclaim]
                  usage: "/claim create [radius]"
                  description: Create or expand a claim centered on you.
            """);
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;
        
        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Console");

        command.onCommand(sender, mock(Command.class), "claim", new String[] { "help" });

        // Verify the help header and entry are sent
        verify(sender, atLeastOnce()).sendMessage("§6Available /claim commands (Page 1/1):");
        verify(sender, atLeastOnce()).sendMessage("");
        // The actual help entry message format
        verify(sender, atLeastOnce()).sendMessage("§e§e/claim create [radius]§7 - Create or expand a claim centered on you.");
    }

    @Test
    void colorOnlyMessagesAreSuppressed(@TempDir Path tempDir) throws Exception {
        CommandAliasConfiguration aliases = loadAliases(tempDir, """
            enabled: true
            commands:
              claim:
                enable: true
                commands: [claim]
            subcommands:
              claim:
                create:
                  enable: false
            """);
        GriefPrevention plugin = newPlugin(aliases);
        GriefPrevention.instance = plugin;

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        command.onCommand(player, mock(Command.class), "claim", new String[] { "create" });

        // Should NOT send any message (color-only messages suppressed)
        verify(player, never()).sendMessage(any(String.class));
    }

    private static CommandAliasConfiguration loadAliases(Path tempDir, String yaml) throws Exception {
        File file = tempDir.resolve("aliases.yml").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml);
        }
        // Create a mock plugin to load the configuration
        GriefPrevention mockPlugin = mock(GriefPrevention.class);
        when(mockPlugin.getLogger()).thenReturn(mock(Logger.class));
        return CommandAliasConfiguration.load(mockPlugin, file);
    }

    private static GriefPrevention newPlugin(CommandAliasConfiguration aliases) {
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        when(plugin.getCommandAliases()).thenReturn(aliases);
        when(plugin.getCommand(anyString())).thenReturn(mock(PluginCommand.class));
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        
        // Setup mock for getMessage with proper varargs handling
        when(dataStore.getMessage(any(Messages.class), any(String.class), any(String.class)))
            .thenAnswer(invocation -> {
                Messages message = invocation.getArgument(0);
                String arg1 = invocation.getArgument(1);
                String arg2 = invocation.getArgument(2);
                
                if (message == Messages.ClaimHelpHeader) {
                    return "§6Available /claim commands (Page 1/1):";
                }
                if (message == Messages.ClaimHelpLegend) {
                    return "";
                }
                if (message == Messages.ClaimHelpEntry) {
                    // Format: §e<command>§7 - <description>
                    return "§e" + arg1 + "§7 - " + arg2;
                }
                return "";
            });
        
        // Handle single-arg getMessage calls
        when(dataStore.getMessage(any(Messages.class)))
            .thenAnswer(invocation -> {
                Messages message = invocation.getArgument(0);
                if (message == Messages.ClaimHelpHeader) {
                    return "§6Available /claim commands (Page 1/1):";
                }
                if (message == Messages.ClaimHelpLegend) {
                    return "";
                }
                return message.name();
            });
        
        when(dataStore.getGroupBonusBlocks(any())).thenReturn(0);
        
        return plugin;
    }
}
