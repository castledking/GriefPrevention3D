package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.test.ServerMocks;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ExtendClaimCommandTest {

    private static final UUID OWNER_ID = UUID.fromString("5e0c2a1d-7b3f-4c8e-9a6d-2f1b4c3d5e7a");
    private static final UUID OTHER_ID = UUID.fromString("c4a7e1b2-3d5f-4e6a-8b9c-0d1e2f3a4b5c");

    // Bukkit yaw -90 faces +X (east); pitch -90 looks straight up.
    private static final float FACE_EAST = -90F;
    private static final float LOOK_UP = -90F;

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
    void standingInA3DSubdivisionOfAShapedClaimExtendsTheSubdivision() {
        Claim shapedClaim = shapedClaim();
        Claim subdivision = subdivision3D(shapedClaim);
        Player owner = player(OWNER_ID, new Location(null, 3, 75, 3, FACE_EAST, 0F));
        PlayerData playerData = new PlayerData();
        DataStore dataStore = dataStore(OWNER_ID, playerData, subdivision);
        AtomicReference<Claim> resized = captureResizedClaim(dataStore, playerData);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handleExtendClaimCommand(owner, new String[] { "1" }));

        verify(dataStore).resizeClaimWithChecks(eq(owner), eq(playerData), eq(2), eq(6), eq(70), eq(80), eq(2), eq(5));
        assertSame(subdivision, resized.get());
        verify(dataStore, never()).updateShapedClaim(any(), any(), any(), any());
    }

    @Test
    void a3DSubdivisionOfAShapedClaimCanGrowUpward() {
        Claim shapedClaim = shapedClaim();
        Claim subdivision = subdivision3D(shapedClaim);
        Player owner = player(OWNER_ID, new Location(null, 3, 75, 3, 0F, LOOK_UP));
        PlayerData playerData = new PlayerData();
        DataStore dataStore = dataStore(OWNER_ID, playerData, subdivision);
        AtomicReference<Claim> resized = captureResizedClaim(dataStore, playerData);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handleExtendClaimCommand(owner, new String[] { "1" }));

        verify(dataStore).resizeClaimWithChecks(eq(owner), eq(playerData), eq(2), eq(5), eq(70), eq(81), eq(2), eq(5));
        assertSame(subdivision, resized.get());
    }

    @Test
    void playersWhoCannotEditAShapedClaimCannotExtendIt() {
        Claim shapedClaim = shapedClaim();
        Player other = player(OTHER_ID, new Location(null, 3, 64, 3, FACE_EAST, 0F));
        DataStore dataStore = dataStore(OTHER_ID, new PlayerData(), shapedClaim);
        GriefPrevention plugin = plugin(dataStore);

        assertTrue(plugin.handleExtendClaimCommand(other, new String[] { "1" }));

        verify(dataStore, never()).updateShapedClaim(any(), any(), any(), any());
        verify(dataStore, never()).resizeClaimWithChecks(any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** An L-shaped claim: x 0..8 for every z, plus z 12..20 for every x. Stored at a single Y like real shaped claims. */
    private static Claim shapedClaim() {
        Claim claim = new Claim(
            new Location(null, 0, 60, 0),
            new Location(null, 20, 60, 20),
            OWNER_ID,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            1L
        );
        claim.setShapedCorners(Arrays.asList(
            new OrthogonalPoint2i(0, 0),
            new OrthogonalPoint2i(8, 0),
            new OrthogonalPoint2i(8, 12),
            new OrthogonalPoint2i(20, 12),
            new OrthogonalPoint2i(20, 20),
            new OrthogonalPoint2i(0, 20)
        ));
        return claim;
    }

    private static Claim subdivision3D(Claim parent) {
        Claim child = new Claim(
            new Location(null, 2, 70, 2),
            new Location(null, 5, 80, 5),
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            2L
        );
        child.set3D(true);
        child.parent = parent;
        parent.children.add(child);
        return child;
    }

    private static Player player(UUID id, Location location) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(location);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(server.getPlayer(id)).thenReturn(player);
        return player;
    }

    private static DataStore dataStore(UUID playerId, PlayerData playerData, Claim standingIn) {
        DataStore dataStore = mock(DataStore.class, invocation ->
            invocation.getMethod().getReturnType() == String.class
                ? "message"
                : RETURNS_DEFAULTS.answer(invocation));
        doReturn(playerData).when(dataStore).getPlayerData(playerId);
        doReturn(standingIn).when(dataStore).getClaimAt(any(Location.class), anyBoolean(), any());
        return dataStore;
    }

    private static AtomicReference<Claim> captureResizedClaim(DataStore dataStore, PlayerData playerData) {
        AtomicReference<Claim> resized = new AtomicReference<>();
        doAnswer(invocation -> {
            resized.set(playerData.claimResizing);
            return null;
        }).when(dataStore).resizeClaimWithChecks(any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return resized;
    }

    private static GriefPrevention plugin(DataStore dataStore) {
        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.dataStore = dataStore;
        GriefPrevention.instance = plugin;
        return plugin;
    }
}
