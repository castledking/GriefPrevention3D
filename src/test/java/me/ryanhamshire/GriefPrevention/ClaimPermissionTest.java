package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimPermissionTest
{
    private static final UUID OWNER_ID = UUID.fromString("3c34f3c7-04b1-46e3-8120-b5dcb6bb5ca7");
    private static final UUID MANAGER_ID = UUID.fromString("f53a8b61-c8a9-4ed5-b2f2-c2a7f7951dc3");

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        PluginManager pluginManager = mock(PluginManager.class);
        doReturn(pluginManager).when(server).getPluginManager();
        Bukkit.setServer(server);

        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;

        when(dataStore.getPlayerData(OWNER_ID)).thenReturn(new PlayerData());
        when(dataStore.getPlayerData(MANAGER_ID)).thenReturn(new PlayerData());
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void permissionTrustDoesNotGrantOwnerOnlyEdit()
    {
        Player manager = mock(Player.class);
        when(manager.getUniqueId()).thenReturn(MANAGER_ID);
        when(Bukkit.getServer().getPlayer(MANAGER_ID)).thenReturn(manager);

        Claim claim = new Claim(
                new Location(null, 0, 64, 0),
                new Location(null, 9, 64, 9),
                OWNER_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(MANAGER_ID.toString()),
                1L);

        assertNull(claim.checkPermission(manager, ClaimPermission.Manage, null));
        assertNotNull(claim.checkPermission(manager, ClaimPermission.Edit, null));
    }
}
