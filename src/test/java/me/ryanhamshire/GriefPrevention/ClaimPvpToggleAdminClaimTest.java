package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ClaimPvpToggleAdminClaimTest {

    private static final UUID STAFF_ID = UUID.fromString("0f3d1a52-7e1c-4c57-9d0e-3a8b6c2f4e19");
    private static final UUID PLAYER_ID = UUID.fromString("b7c2e4a1-5d3f-4b8e-a6c9-1e2d3f4a5b6c");

    private static Server server;

    @BeforeAll
    static void beforeAll() {
        server = ServerMocks.newServer();
        doReturn(mock(PluginManager.class)).when(server).getPluginManager();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void staffCanDisablePvpInAnAdminClaim() {
        Claim adminClaim = adminClaim(1L);
        Player staff = player(STAFF_ID, true);
        DataStore dataStore = dataStore(staff, adminClaim);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handlePvpCommand(staff, new String[] { "disable", "confirm" }));

        verify(dataStore).saveClaim(adminClaim);
        assertFalse(adminClaim.pvpEnabled);
        assertTrue(adminClaim.pvpToggleSet);
    }

    @Test
    void staffCanDisablePvpInAnAdminClaimSubdivision() {
        Claim adminClaim = adminClaim(2L);
        Claim subdivision = new Claim(
            new Location(null, 2, 64, 2),
            new Location(null, 8, 64, 8),
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            3L
        );
        subdivision.parent = adminClaim;
        adminClaim.children.add(subdivision);

        Player staff = player(STAFF_ID, true);
        DataStore dataStore = dataStore(staff, subdivision);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handlePvpCommand(staff, new String[] { "disable", "confirm" }));

        verify(dataStore).saveClaim(subdivision);
        assertFalse(subdivision.pvpEnabled);
    }

    @Test
    void regularPlayersCannotTogglePvpInAnAdminClaim() {
        Claim adminClaim = adminClaim(4L);
        Player regular = player(PLAYER_ID, false);
        DataStore dataStore = dataStore(regular, adminClaim);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handlePvpCommand(regular, new String[] { "disable", "confirm" }));

        verify(dataStore, never()).saveClaim(adminClaim);
    }

    private static Player player(UUID id, boolean adminClaimsPermission) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(null, 5, 64, 5));
        when(player.hasPermission("griefprevention.adminclaims")).thenReturn(adminClaimsPermission);
        when(server.getPlayer(id)).thenReturn(player);
        return player;
    }

    private static Claim adminClaim(long id) {
        return new Claim(
            new Location(null, 0, 0, 0),
            new Location(null, 20, 255, 20),
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }

    private static DataStore dataStore(Player player, Claim claim) {
        DataStore dataStore = mock(DataStore.class, invocation ->
            invocation.getMethod().getReturnType() == String.class
                ? "message"
                : RETURNS_DEFAULTS.answer(invocation));
        UUID playerId = player.getUniqueId();
        PlayerData playerData = new PlayerData();
        doReturn(playerData).when(dataStore).getPlayerData(playerId);
        doReturn(claim).when(dataStore).getClaimAt(any(Location.class), anyBoolean(), any());
        return dataStore;
    }

    private static GriefPrevention plugin(DataStore dataStore) {
        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.dataStore = dataStore;
        plugin.config_pvp_toggleCostClaimEnabled = true;
        plugin.config_pvp_toggleCostSubdivisionEnabled = true;
        plugin.config_pvp_toggleCostClaimPrice = 0.0;
        plugin.config_pvp_toggleCostSubdivisionPrice = 0.0;
        GriefPrevention.instance = plugin;
        return plugin;
    }
}
