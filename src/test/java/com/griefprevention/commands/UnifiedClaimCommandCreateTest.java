package com.griefprevention.commands;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedClaimCommandCreateTest
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
    void createSubcommandUsesExactTenByTenBoundsForFirstClaim()
    {
        UUID playerId = UUID.fromString("1f4d8f80-40c2-4b2c-852d-6ef1b3ea11c7");

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.config_claims_maxClaimsPerPlayer = 0;
        plugin.config_claims_automaticClaimsForNewPlayersRadius = 4;
        plugin.config_claims_minWidth = 5;
        plugin.config_claims_minArea = 100;
        plugin.config_claims_claimsExtendIntoGroundDistance = 5;

        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        setClaimsList(dataStore, new ArrayList<>());
        GriefPrevention.instance = plugin;

        when(plugin.getCommandAliases()).thenReturn(CommandAliasConfiguration.empty());
        when(plugin.claimsEnabledForWorld(any(World.class))).thenReturn(true);
        when(plugin.getCommand(anyString())).thenReturn(mock(PluginCommand.class));
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));

        World world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(any(Location.class))).thenReturn(70);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 100.5D, 64D, 200.5D));

        PlayerData playerData = new PlayerData();
        playerData.playerID = playerId;
        playerData.setAccruedClaimBlocks(10_000);
        playerData.setBonusClaimBlocks(0);
        when(dataStore.getPlayerData(playerId)).thenReturn(playerData);
        when(dataStore.getGroupBonusBlocks(playerId)).thenReturn(0);

        AtomicInteger capturedX1 = new AtomicInteger();
        AtomicInteger capturedX2 = new AtomicInteger();
        AtomicInteger capturedZ1 = new AtomicInteger();
        AtomicInteger capturedZ2 = new AtomicInteger();

        doAnswer(invocation -> {
            capturedX1.set(invocation.getArgument(1));
            capturedX2.set(invocation.getArgument(2));
            capturedZ1.set(invocation.getArgument(5));
            capturedZ2.set(invocation.getArgument(6));

            CreateClaimResult result = new CreateClaimResult();
            result.succeeded = false;
            result.claim = null;
            return result;
        }).when(dataStore).createClaim(
                eq(world),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                eq(playerId),
                any(),
                any(),
                eq(player));

        UnifiedClaimCommand command = new UnifiedClaimCommand(plugin);

        boolean handled = command.onCommand(player, mock(Command.class), "claim", new String[] { "create" });

        assertEquals(true, handled);
        assertEquals(96, capturedX1.get());
        assertEquals(105, capturedX2.get());
        assertEquals(196, capturedZ1.get());
        assertEquals(205, capturedZ2.get());
    }

    private static void setClaimsList(DataStore dataStore, ArrayList<?> claims)
    {
        try
        {
            Field claimsField = DataStore.class.getDeclaredField("claims");
            claimsField.setAccessible(true);
            claimsField.set(dataStore, claims);
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }
}
