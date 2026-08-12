package com.griefprevention.commands;

import com.griefprevention.claims.ClaimTrustCommandPermissions;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class UnifiedAdminClaimPermissionTrustTest
{
    private GriefPrevention plugin;
    private Player player;
    private PluginCommand permissionTrustCommand;
    private UnifiedAdminClaimCommand command;

    @BeforeEach
    void setUp()
    {
        this.plugin = mock(GriefPrevention.class);
        this.player = mock(Player.class);
        this.permissionTrustCommand = mock(PluginCommand.class);
        PluginCommand otherCommand = mock(PluginCommand.class);

        when(this.plugin.getCommandAliases()).thenReturn(CommandAliasConfiguration.empty());
        when(this.plugin.getCommand(anyString())).thenAnswer(invocation ->
                "permissiontrust".equals(invocation.getArgument(0))
                        ? this.permissionTrustCommand
                        : otherCommand
        );
        when(this.player.hasPermission(ClaimTrustCommandPermissions.PERMISSION_TRUST)).thenReturn(true);

        this.command = new UnifiedAdminClaimCommand(this.plugin);
    }

    @Test
    void adminClaimFormRoutesPermissionNodeAndManagerTypeToSharedTrustMutation()
    {
        boolean handled = this.command.onCommand(
                this.player,
                mock(Command.class),
                "aclaim",
                new String[] { "trust", "permission", "[server.managers]", "manager" }
        );

        assertTrue(handled);
        verify(this.plugin).handleTrustCommand(
                this.player,
                ClaimPermission.Manage,
                "[server.managers]",
                false
        );
    }

    @Test
    void standaloneFormNormalizesBareNodeAndInventoryAlias()
    {
        ArgumentCaptor<CommandExecutor> executor = ArgumentCaptor.forClass(CommandExecutor.class);
        verify(this.permissionTrustCommand).setExecutor(executor.capture());

        boolean handled = executor.getValue().onCommand(
                this.player,
                this.permissionTrustCommand,
                "permissiontrust",
                new String[] { "server.builders", "inventory" }
        );

        assertTrue(handled);
        verify(this.plugin).handleTrustCommand(
                this.player,
                ClaimPermission.Container,
                "[server.builders]",
                false
        );
    }

    @Test
    void standaloneFormCompletesTrustTypesInTheTypePosition()
    {
        ArgumentCaptor<TabCompleter> completer = ArgumentCaptor.forClass(TabCompleter.class);
        verify(this.permissionTrustCommand).setTabCompleter(completer.capture());

        assertEquals(
                Collections.singletonList("container"),
                completer.getValue().onTabComplete(
                        this.player,
                        this.permissionTrustCommand,
                        "permissiontrust",
                        new String[] { "server.builders", "c" }
                )
        );
    }
}
