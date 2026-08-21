package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.griefprevention.claims.ClaimTrustSnapshot;
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
class CombatTrustTest {

    private static final UUID OWNER_ID = UUID.fromString("3c34f3c7-04b1-46e3-8120-b5dcb6bb5ca7");
    private static final UUID FIGHTER_ID = UUID.fromString("6b2b9d0e-9d0a-4a3a-9f5e-1d7c3a5b8e42");
    private static final UUID CONTAINER_ID = UUID.fromString("f53a8b61-c8a9-4ed5-b2f2-c2a7f7951dc3");

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
        when(dataStore.getPlayerData(FIGHTER_ID)).thenReturn(new PlayerData());
        when(dataStore.getPlayerData(CONTAINER_ID)).thenReturn(new PlayerData());
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void combatTrustIsNeverImpliedByOtherTrustLevels() {
        Claim claim = claim(1L);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.Manage);

        assertNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.Build, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.PVP, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.PVE, null));
    }

    @Test
    void combatTrustImpliesNothingElse() {
        Claim claim = claim(2L);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVP);

        assertNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.PVP, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.Access, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.Container, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.Build, null));
    }

    @Test
    void ownerAlwaysHasCombatTrust() {
        Claim claim = claim(3L);

        assertNull(claim.checkPermission(player(OWNER_ID), ClaimPermission.PVP, null));
        assertNull(claim.checkPermission(player(OWNER_ID), ClaimPermission.PVE, null));
    }

    @Test
    void grantingCombatTrustKeepsContainerTrustAndViceVersa() {
        Claim claim = claim(4L);
        claim.setPermission(CONTAINER_ID.toString(), ClaimPermission.Container);
        claim.setPermission(CONTAINER_ID.toString(), ClaimPermission.PVE);

        assertNull(claim.checkPermission(player(CONTAINER_ID), ClaimPermission.Container, null));
        assertNull(claim.checkPermission(player(CONTAINER_ID), ClaimPermission.PVE, null));
        assertNotNull(claim.checkPermission(player(CONTAINER_ID), ClaimPermission.PVP, null));
    }

    @Test
    void untrustRemovesCombatTrustToo() {
        Claim claim = claim(5L);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVP);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVE);

        claim.dropPermission(FIGHTER_ID.toString());

        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.PVP, null));
        assertNotNull(claim.checkPermission(player(FIGHTER_ID), ClaimPermission.PVE, null));
    }

    @Test
    void droppingCombatTrustKeepsOtherTrust() {
        Claim claim = claim(6L);
        claim.setPermission(CONTAINER_ID.toString(), ClaimPermission.Container);
        claim.setPermission(CONTAINER_ID.toString(), ClaimPermission.PVP);
        claim.dropPvpTrust(CONTAINER_ID.toString());

        assertNull(claim.checkPermission(player(CONTAINER_ID), ClaimPermission.Container, null));
        assertNotNull(claim.checkPermission(player(CONTAINER_ID), ClaimPermission.PVP, null));
    }

    @Test
    void combatTrustInheritsIntoFirstChildSubdivisionsOnly() {
        Claim parent = claim(7L);
        parent.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVP);

        Claim child = subdivision(parent, 8L);
        Claim grandchild = subdivision(child, 9L);

        assertNull(child.checkPermission(player(FIGHTER_ID), ClaimPermission.PVP, null),
                "first-child subdivisions inherit parent trust");
        assertNotNull(grandchild.checkPermission(player(FIGHTER_ID), ClaimPermission.PVP, null),
                "nested subdivisions do not inherit");
    }

    @Test
    void subdivisionDenyBlocksInheritedCombatTrust() {
        Claim parent = claim(10L);
        parent.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVE);

        Claim child = subdivision(parent, 11L);
        child.restoreDeniedPermissions(Collections.singletonList(FIGHTER_ID + "#pve"));

        assertNotNull(child.checkPermission(player(FIGHTER_ID), ClaimPermission.PVE, null),
                "explicit deny overrides inherited grant");
    }

    @Test
    void trustSnapshotCarriesCombatTrust() {
        Claim claim = claim(12L);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVP);
        claim.setPermission(CONTAINER_ID.toString(), ClaimPermission.PVE);

        ClaimTrustSnapshot snapshot = claim.getTrustSnapshot();

        assertTrue(snapshot.pvpTrustedIdentifiers().contains(FIGHTER_ID.toString()));
        assertTrue(snapshot.pveTrustedIdentifiers().contains(CONTAINER_ID.toString()));
        assertFalse(snapshot.permissionsByIdentifier().containsKey(FIGHTER_ID.toString()),
                "combat trust must not leak into the interaction trust map");
    }

    @Test
    void clearPermissionsClearsCombatTrust() {
        Claim claim = claim(13L);
        claim.setPermission(FIGHTER_ID.toString(), ClaimPermission.PVP);
        claim.clearPermissions();

        assertTrue(claim.getPvpTrustedIdentifiers().isEmpty());
        assertFalse(claim.isPvpTrusted(FIGHTER_ID.toString()));
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(Bukkit.getServer().getPlayer(id)).thenReturn(player);
        return player;
    }

    private static Claim claim(long id) {
        return new Claim(
            new Location(null, 0, 64, 0),
            new Location(null, 9, 64, 9),
            OWNER_ID,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }

    private static Claim subdivision(Claim parent, long id) {
        Claim child = new Claim(
            new Location(null, 2, 64, 2),
            new Location(null, 8, 64, 8),
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
        child.parent = parent;
        parent.children.add(child);
        return child;
    }
}
