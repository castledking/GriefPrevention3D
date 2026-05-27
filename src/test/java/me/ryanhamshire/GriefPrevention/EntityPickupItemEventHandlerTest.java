package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class EntityPickupItemEventHandlerTest
{
    private Server server;

    @BeforeEach
    void beforeEach()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterEach
    void afterEach()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void pvpFreshSpawnImmunityEndsWhenPlayerPicksUpAnItem()
    {
        UUID playerId = UUID.fromString("536bd1e1-2f73-49ef-8cc9-8731c39ad566");
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        plugin.config_pvp_protectFreshSpawns = true;
        GriefPrevention.instance = plugin;

        World world = mock(World.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(plugin.pvpRulesApply(world)).thenReturn(true);
        when(plugin.getItemInHand(player, EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.AIR));
        when(dataStore.getMessage(eq(Messages.PvPImmunityEnd), any(String[].class))).thenReturn("Now you can fight with other players.");

        PlayerData playerData = new PlayerData();
        playerData.playerID = playerId;
        playerData.pvpImmune = true;
        when(dataStore.getPlayerData(playerId)).thenReturn(playerData);

        Item item = mock(Item.class);
        when(item.getMetadata("GP_ITEMOWNER")).thenReturn(Collections.emptyList());
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(item);

        new EntityPickupItemEventHandler(dataStore).onEntityPickUpItem(event);

        verify(event, never()).setCancelled(true);
        verify(player).sendMessage("§6Now you can fight with other players.");
        org.junit.jupiter.api.Assertions.assertFalse(playerData.pvpImmune);
    }

    @Test
    void ownerCanPickUpTheirOwnLockedDeathDropsByUuid()
    {
        UUID playerId = UUID.fromString("358f3aaf-8d8a-48ff-8242-b2f5e5082413");
        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        OfflinePlayer owner = mock(OfflinePlayer.class);
        when(owner.isOnline()).thenReturn(true);
        when(server.getOfflinePlayer(playerId)).thenReturn(owner);
        when(plugin.getServer()).thenReturn(server);

        MetadataValue ownerMetadata = mock(MetadataValue.class);
        when(ownerMetadata.value()).thenReturn(playerId);

        Item item = mock(Item.class);
        when(item.getMetadata("GP_ITEMOWNER")).thenReturn(List.of(ownerMetadata));
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(item);

        new EntityPickupItemEventHandler(dataStore).onEntityPickUpItem(event);

        verify(event, never()).setCancelled(true);
        verify(dataStore, never()).getPlayerData(playerId);
    }
}
