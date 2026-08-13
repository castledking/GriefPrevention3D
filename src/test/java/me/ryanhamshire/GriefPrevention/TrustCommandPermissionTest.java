package me.ryanhamshire.GriefPrevention;

import com.griefprevention.claims.ClaimTrustCommandPermissions;
import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class TrustCommandPermissionTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID CLAIM_OWNER = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final UUID RECIPIENT = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static Server server;
    private static PluginManager pluginManager;

    private GriefPrevention plugin;
    private DataStore dataStore;
    private Player player;
    private Claim claim;

    @BeforeAll
    static void beforeAll()
    {
        server = ServerMocks.newServer();
        pluginManager = mock(PluginManager.class);
        doReturn(pluginManager).when(server).getPluginManager();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        this.plugin = mock(GriefPrevention.class);
        this.dataStore = mock(DataStore.class);
        this.player = mock(Player.class);
        World world = mock(World.class);
        Location location = new Location(world, 5, 70, 5);
        PlayerData playerData = new PlayerData();
        playerData.playerID = OWNER;
        this.claim = new Claim(
                new Location(world, 0, 60, 0),
                new Location(world, 10, 80, 10),
                OWNER,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                false,
                1L,
                true
        );

        this.plugin.dataStore = this.dataStore;
        GriefPrevention.instance = this.plugin;
        when(this.plugin.getServer()).thenReturn(server);
        when(this.plugin.isActionBarMessage(any(Messages.class))).thenReturn(false);
        when(this.player.getUniqueId()).thenReturn(OWNER);
        when(this.player.getLocation()).thenReturn(location);
        when(this.dataStore.getPlayerData(OWNER)).thenReturn(playerData);
        when(this.dataStore.getClaimAt(any(Location.class), anyBoolean(), any())).thenReturn(this.claim);
        doCallRealMethod().when(this.plugin).handleTrustCommand(
                any(Player.class),
                any(ClaimPermission.class),
                any(String.class),
                anyBoolean()
        );
    }

    @Test
    void dottedPermissionTargetIsGrantedWithoutFallingThroughToPublic()
    {
        when(this.player.hasPermission(ClaimTrustCommandPermissions.PERMISSION_TRUST)).thenReturn(true);

        this.plugin.handleTrustCommand(
                this.player,
                ClaimPermission.Container,
                "gp3d.test.container",
                false
        );

        assertEquals(ClaimPermission.Container, this.claim.getPermission("[gp3d.test.container]"));
        assertNull(this.claim.getPermission("public"));
        verify(this.dataStore).saveClaims(anyList());
    }

    @Test
    void permissionTargetGrantIsRejectedWithoutTheAdministrativeChildPermission()
    {
        when(this.player.hasPermission(ClaimTrustCommandPermissions.PERMISSION_TRUST)).thenReturn(false);

        this.plugin.handleTrustCommand(
                this.player,
                ClaimPermission.Build,
                "[gp3d.test.builder]",
                false
        );

        assertNull(this.claim.getPermission("[gp3d.test.builder]"));
        verify(this.dataStore, never()).saveClaims(anyList());
    }

    @Test
    void managerCanGrantBuildTrustToAnotherPlayer()
    {
        configureManagerClaimAndRecipient();

        this.plugin.handleTrustCommand(this.player, ClaimPermission.Build, "Linkondorf", false);

        assertEquals(ClaimPermission.Build, this.claim.getPermission(RECIPIENT.toString()));
        verify(this.dataStore).saveClaims(anyList());
    }

    @Test
    void managerCanGrantManageTrustToAnotherPlayer()
    {
        configureManagerClaimAndRecipient();

        this.plugin.handleTrustCommand(this.player, ClaimPermission.Manage, "Linkondorf", false);

        assertTrue(this.claim.isManager(RECIPIENT.toString()));
        verify(this.dataStore).saveClaims(anyList());
    }

    @Test
    void ownerCanGrantBuildAndManageTrustWithoutOverwritingEither()
    {
        configureRecipient();

        this.plugin.handleTrustCommand(this.player, ClaimPermission.Build, "Linkondorf", false);
        this.plugin.handleTrustCommand(this.player, ClaimPermission.Manage, "Linkondorf", false);

        assertEquals(ClaimPermission.Build, this.claim.getPermission(RECIPIENT.toString()));
        assertTrue(this.claim.isManager(RECIPIENT.toString()));
        verify(this.dataStore, times(2)).saveClaims(anyList());
    }

    private void configureManagerClaimAndRecipient()
    {
        World world = this.player.getLocation().getWorld();
        this.claim = new Claim(
                new Location(world, 0, 60, 0),
                new Location(world, 10, 80, 10),
                CLAIM_OWNER,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(Collections.singletonList(OWNER.toString())),
                false,
                2L,
                true
        );
        when(this.dataStore.getClaimAt(any(Location.class), anyBoolean(), any())).thenReturn(this.claim);

        configureRecipient();
    }

    private void configureRecipient()
    {
        OfflinePlayer recipient = mock(OfflinePlayer.class);
        when(recipient.getUniqueId()).thenReturn(RECIPIENT);
        when(recipient.getName()).thenReturn("Linkondorf");
        doReturn(recipient).when(this.plugin).resolvePlayerByName("Linkondorf");
    }
}
