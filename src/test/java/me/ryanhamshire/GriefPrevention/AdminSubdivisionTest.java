package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
class AdminSubdivisionTest {

    private static final UUID OWNER_ID = UUID.fromString("2a2a4e2f-2b0a-4a76-9b17-6a2c8a3e6b21");
    private static final UUID STAFF_ID = UUID.fromString("8f7ab1cd-8a5f-4f0e-9f22-3f2e0e6cd9a4");

    @BeforeAll
    static void beforeAll() {
        Server server = ServerMocks.newServer();
        PluginManager pluginManager = mock(PluginManager.class);
        doReturn(pluginManager).when(server).getPluginManager();
        Bukkit.setServer(server);

        GriefPrevention plugin = mock(GriefPrevention.class);
        DataStore dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;

        when(dataStore.getPlayerData(OWNER_ID)).thenReturn(new PlayerData());
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(new PlayerData());
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void ownerLosesEditAndManageInsideAnAdministrativeSubdivision() {
        Claim parent = claim(1L, OWNER_ID, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 2L, 4, 4, 8, 8);
        subdivision.setAdminSubdivision(true);

        Player owner = player(OWNER_ID);

        assertNull(parent.checkPermission(owner, ClaimPermission.Edit, null),
                "the owner still controls the claim around the administrative subdivision");
        assertNotNull(subdivision.checkPermission(owner, ClaimPermission.Edit, null));
        assertNotNull(subdivision.checkPermission(owner, ClaimPermission.Manage, null));
    }

    @Test
    void staffKeepEditAndManageInsideAnAdministrativeSubdivision() {
        Claim parent = claim(3L, OWNER_ID, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 4L, 4, 4, 8, 8);
        subdivision.setAdminSubdivision(true);

        Player staff = player(STAFF_ID);
        when(staff.hasPermission("griefprevention.adminclaims")).thenReturn(true);

        assertNull(subdivision.checkPermission(staff, ClaimPermission.Edit, null));
        assertNull(subdivision.checkPermission(staff, ClaimPermission.Manage, null));
    }

    @Test
    void aNormalSubdivisionStillInheritsOwnerControl() {
        Claim parent = claim(5L, OWNER_ID, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 6L, 4, 4, 8, 8);

        Player owner = player(OWNER_ID);

        assertNull(subdivision.checkPermission(owner, ClaimPermission.Edit, null));
        assertNull(subdivision.checkPermission(owner, ClaimPermission.Manage, null));
    }

    @Test
    void anAdministrativeSubdivisionIsVisibleFromTheClaimAroundIt() {
        Claim parent = claim(7L, OWNER_ID, 0, 0, 20, 20);
        Claim subdivision = subdivision(parent, 8L, 4, 4, 8, 8);
        Claim nested = subdivision(subdivision, 9L, 5, 5, 6, 6);

        assertFalse(parent.containsAdminSubdivision());

        nested.setAdminSubdivision(true);

        assertTrue(parent.containsAdminSubdivision(), "nested administrative subdivisions still pin the root claim");
        assertTrue(subdivision.containsAdminSubdivision());
        assertFalse(nested.containsAdminSubdivision());
    }

    @Test
    void aTopLevelClaimIsNeverAnAdministrativeSubdivision() {
        Claim claim = claim(10L, OWNER_ID, 0, 0, 20, 20);
        claim.setAdminSubdivision(true);

        assertFalse(claim.isAdminSubdivision());
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(Bukkit.getServer().getPlayer(id)).thenReturn(player);
        return player;
    }

    private static Claim claim(long id, UUID owner, int minX, int minZ, int maxX, int maxZ) {
        return new Claim(
            new Location(null, minX, 64, minZ),
            new Location(null, maxX, 64, maxZ),
            owner,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }

    private static Claim subdivision(Claim parent, long id, int minX, int minZ, int maxX, int maxZ) {
        Claim child = claim(id, null, minX, minZ, maxX, maxZ);
        child.parent = parent;
        parent.children.add(child);
        return child;
    }
}
