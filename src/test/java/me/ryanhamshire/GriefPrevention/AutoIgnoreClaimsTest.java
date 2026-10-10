package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code GriefPrevention.AutoIgnoreClaims} grants staff a session with claim protections off,
 * and {@code GriefPrevention.RequireIgnoreClaimsInAdminClaims} demands that staff ignore claims
 * before building in an administrative claim.
 */
@SuppressWarnings("null")
class AutoIgnoreClaimsTest
{
    private static final UUID STAFF_ID = UUID.fromString("9c1d4f8b-7a2e-4d61-8f03-5b7e2c9a4d13");
    private static final UUID PLAYER_ID = UUID.fromString("4e7a2c1d-9b3f-4a58-8c62-1d0f5b8e3a27");

    private static GriefPrevention plugin;
    private static DataStore dataStore;

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        doReturn(mock(PluginManager.class)).when(server).getPluginManager();
        Bukkit.setServer(server);

        plugin = mock(GriefPrevention.class);
        dataStore = mock(DataStore.class);
        when(dataStore.loadBannedWords()).thenReturn(Collections.emptyList());
        plugin.dataStore = dataStore;
        // PlayerEventHandler's constructor builds MonitoredCommands from these lists
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        GriefPrevention.instance = plugin;
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @AfterEach
    void resetConfig()
    {
        plugin.config_autoIgnoreClaims = false;
        plugin.config_requireIgnoreClaimsInAdminClaims = false;
    }

    @Test
    void aSessionGrantCountsAsIgnoringClaims()
    {
        PlayerData playerData = new PlayerData();
        assertFalse(playerData.isIgnoringClaims());

        playerData.autoIgnoreClaims = true;
        assertTrue(playerData.isIgnoringClaims());

        playerData.autoIgnoreClaims = false;
        playerData.ignoreClaims = true;
        assertTrue(playerData.isIgnoringClaims());
    }

    @Test
    void staffWithThePermissionAreGrantedASessionOnJoin()
    {
        plugin.config_autoIgnoreClaims = true;
        PlayerData playerData = new PlayerData();

        newHandler().applyAutoIgnoreClaims(staff(true), playerData);

        assertTrue(playerData.autoIgnoreClaims);
    }

    @Test
    void staffWithoutThePermissionGetNothing()
    {
        plugin.config_autoIgnoreClaims = true;
        PlayerData playerData = new PlayerData();

        newHandler().applyAutoIgnoreClaims(staff(false), playerData);

        assertFalse(playerData.autoIgnoreClaims);
    }

    @Test
    void nobodyIsGrantedASessionWhileTheOptionIsOff()
    {
        PlayerData playerData = new PlayerData();

        newHandler().applyAutoIgnoreClaims(staff(true), playerData);

        assertFalse(playerData.autoIgnoreClaims);
    }

    @Test
    void ignoreClaimsTurnsProtectionsBackOnForTheSession()
    {
        PlayerData playerData = new PlayerData();
        playerData.autoIgnoreClaims = true;
        doReturn(playerData).when(dataStore).getPlayerData(STAFF_ID);

        GriefPrevention real = mock(GriefPrevention.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        real.dataStore = dataStore;
        real.toggleIgnoreClaims(staff(true));

        assertFalse(playerData.autoIgnoreClaims);
        assertFalse(playerData.isIgnoringClaims());
    }

    @Test
    void staffBuildInAdminClaimsUnchangedByDefault()
    {
        PlayerData staffData = new PlayerData();
        staff(true);
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(staffData);

        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Build, null));
    }

    @Test
    void adminClaimsRefuseStaffWhoAreNotIgnoringThem()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        PlayerData staffData = new PlayerData();
        staff(true);
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(staffData);
        when(dataStore.getPlayerData(PLAYER_ID)).thenReturn(new PlayerData());
        when(dataStore.getMessage(any(Messages.class), any(String[].class)))
                .thenAnswer(call -> call.getArgument(0).toString());

        assertNotNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Build, null));
        assertNotNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Container, null));
    }

    @Test
    void theRefusalUsesTheOrdinaryDenialAndPointsAtIgnoreClaims()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        staff(true);
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(new PlayerData());
        when(dataStore.getMessage(any(Messages.class), any(String[].class)))
                .thenAnswer(call -> call.getArgument(0).toString());

        String build = adminClaim().checkPermission(STAFF_ID, ClaimPermission.Build, null).get();
        String container = adminClaim().checkPermission(STAFF_ID, ClaimPermission.Container, null).get();

        // the ordinary per-permission denial, so each protection says its own reason
        assertTrue(build.contains(ClaimPermission.Build.getDenialMessage().toString()));
        assertTrue(container.contains(ClaimPermission.Container.getDenialMessage().toString()));

        // plus the existing /ignoreclaims hint
        assertTrue(build.contains(Messages.IgnoreClaimsAdvertisement.toString()));
        assertTrue(container.contains(Messages.IgnoreClaimsAdvertisement.toString()));
    }

    @Test
    void theRefusalIsSilentForStaffWhoCannotRunIgnoreClaims()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(new PlayerData());
        when(dataStore.getMessage(any(Messages.class), any(String[].class)))
                .thenAnswer(call -> call.getArgument(0).toString());
        Player noIgnore = mock(Player.class);
        when(noIgnore.getUniqueId()).thenReturn(STAFF_ID);
        when(noIgnore.hasPermission("griefprevention.adminclaims")).thenReturn(true);
        doReturn(noIgnore).when(Bukkit.getServer()).getPlayer(STAFF_ID);

        String build = adminClaim().checkPermission(noIgnore, ClaimPermission.Build, null).get();

        assertFalse(build.contains(Messages.IgnoreClaimsAdvertisement.toString()));
    }

    @Test
    void adminClaimsLetStaffBuildOnceTheyIgnoreClaims()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        PlayerData staffData = new PlayerData();
        staffData.autoIgnoreClaims = true;
        staff(true);
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(staffData);

        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Build, null));
        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Container, null));
    }

    @Test
    void adminClaimsStayWalkableAndManageableWithoutIgnoringThem()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        staff(true);
        when(dataStore.getPlayerData(STAFF_ID)).thenReturn(new PlayerData());

        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Access, null));
        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Edit, null));
        assertNull(adminClaim().checkPermission(STAFF_ID, ClaimPermission.Manage, null));
    }

    @Test
    void theOptionOnlyBindsStaffWhoHoldAdminClaims()
    {
        plugin.config_requireIgnoreClaimsInAdminClaims = true;
        when(dataStore.getPlayerData(PLAYER_ID)).thenReturn(new PlayerData());

        // a player without the admin permission was already refused; the option changes nothing
        assertNotNull(adminClaim().checkPermission(PLAYER_ID, ClaimPermission.Build, null));
    }

    private static PlayerEventHandler newHandler()
    {
        return new PlayerEventHandler(dataStore, plugin);
    }

    private static Claim adminClaim()
    {
        return new Claim(
                new Location(null, 0, 64, 0),
                new Location(null, 9, 64, 9),
                null,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                1L);
    }

    private static Player staff(boolean hasAutoIgnore)
    {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(STAFF_ID);
        when(player.getName()).thenReturn("Staff");
        when(player.hasPermission("griefprevention.autoignoreclaims")).thenReturn(hasAutoIgnore);
        when(player.hasPermission("griefprevention.adminclaims")).thenReturn(true);
        when(player.hasPermission("griefprevention.ignoreclaims")).thenReturn(true);
        doReturn(player).when(Bukkit.getServer()).getPlayer(STAFF_ID);
        return player;
    }
}
