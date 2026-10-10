package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Players giving away their own claims with /transferclaim, and staff transfers. */
@SuppressWarnings("null")
class ClaimTransferCommandTest {

    private static final UUID OWNER_ID = UUID.fromString("b7c2e4a1-5d3f-4b8e-a6c9-1e2d3f4a5b6c");
    private static final UUID RECIPIENT_ID = UUID.fromString("0f3d1a52-7e1c-4c57-9d0e-3a8b6c2f4e19");
    private static final UUID STRANGER_ID = UUID.fromString("5a1e9c3b-2d4f-4e6a-8b7c-9d0e1f2a3b4c");
    private static final String RECIPIENT_NAME = "Recipient";

    private static Server server;
    private static World world;

    @BeforeAll
    static void beforeAll() {
        server = ServerMocks.newServer();
        doReturn(mock(PluginManager.class)).when(server).getPluginManager();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Bukkit.setServer(server);
    }

    @AfterEach
    void forgetNames() {
        GriefPrevention.playerNameToIDMap.clear();
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void playersCannotGiveClaimsAwayUntilTheServerEnablesIt() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);
        fixture.plugin.config_claims_transferClaimEnabled = false;

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        verify(fixture.dataStore, never()).tryChangeClaimOwner(any(), any());
    }

    @Test
    void playersCanOnlyGiveAwayClaimsTheyOwn() {
        Fixture fixture = new Fixture(STRANGER_ID, 1000);

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        verify(fixture.dataStore, never()).tryChangeClaimOwner(any(), any());
        assertEquals(STRANGER_ID, fixture.claim.ownerID);
    }

    @Test
    void theRecipientMustHaveTheClaimBlocksForIt() {
        Fixture fixture = new Fixture(OWNER_ID, 50);

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        verify(fixture.dataStore, never()).tryChangeClaimOwner(any(), any());
    }

    @Test
    void theFirstRequestOnlyAsksForConfirmation() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);

        assertTrue(fixture.transfer(RECIPIENT_NAME));

        verify(fixture.dataStore, never()).tryChangeClaimOwner(any(), any());
    }

    @Test
    void aConfirmedFreeTransferHandsTheClaimOver() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        verify(fixture.dataStore).tryChangeClaimOwner(fixture.claim, RECIPIENT_ID);
        assertEquals(RECIPIENT_ID, fixture.claim.ownerID);
    }

    @Test
    void aPricedTransferNeedsAnEconomy() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);
        fixture.plugin.config_claims_transferClaimPrice = 250.0;

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        verify(fixture.dataStore, never()).tryChangeClaimOwner(any(), any());
        assertEquals(OWNER_ID, fixture.claim.ownerID);
    }

    @Test
    void theFreePermissionSkipsThePrice() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);
        fixture.plugin.config_claims_transferClaimPrice = 250.0;
        when(fixture.owner.hasPermission("griefprevention.transferclaim.free")).thenReturn(true);

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        assertEquals(RECIPIENT_ID, fixture.claim.ownerID);
    }

    @Test
    void staffTransferAnyClaimAtOnce() {
        Fixture fixture = new Fixture(STRANGER_ID, 0);
        fixture.plugin.config_claims_transferClaimEnabled = false;
        when(fixture.owner.hasPermission("griefprevention.transferclaim.others")).thenReturn(true);

        assertTrue(fixture.transfer(RECIPIENT_NAME));

        assertEquals(RECIPIENT_ID, fixture.claim.ownerID);
    }

    @Test
    void aCancelledStaffTransferIsNotReportedAsSuccess() {
        Fixture fixture = new Fixture(STRANGER_ID, 0);
        when(fixture.owner.hasPermission("griefprevention.transferclaim.others")).thenReturn(true);
        fixture.cancelTransfers();

        assertTrue(fixture.transfer(RECIPIENT_NAME));

        assertEquals(STRANGER_ID, fixture.claim.ownerID);
        verify(fixture.owner, never()).sendMessage(anyString());
    }

    @Test
    void aCancelledGiveAwayIsNotReportedAsSuccess() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);
        fixture.cancelTransfers();

        assertTrue(fixture.transfer(RECIPIENT_NAME, "confirm"));

        assertEquals(OWNER_ID, fixture.claim.ownerID);
        verify(fixture.owner, never()).sendMessage(anyString());
    }

    @Test
    void wrongArgumentsShowTheUsage() {
        Fixture fixture = new Fixture(OWNER_ID, 1000);

        assertFalse(fixture.transfer());
        assertFalse(fixture.transfer(RECIPIENT_NAME, "please"));
    }

    /** The player running the command stands in a 10x10 top-level claim owned by {@code claimOwner}. */
    private static final class Fixture {
        private final Player owner;
        private final Claim claim;
        private final DataStore dataStore;
        private final GriefPrevention plugin;

        private Fixture(UUID claimOwner, int recipientAccruedBlocks) {
            this.owner = mock(Player.class);
            when(this.owner.getUniqueId()).thenReturn(OWNER_ID);
            when(this.owner.getName()).thenReturn("Owner");
            when(this.owner.getLocation()).thenReturn(new Location(world, 5, 64, 5));
            when(this.owner.hasPermission("griefprevention.transferclaim")).thenReturn(true);

            this.claim = new Claim(
                new Location(world, 0, 0, 0),
                new Location(world, 9, 255, 9),
                claimOwner,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                1L
            );

            this.dataStore = mock(DataStore.class, invocation ->
                invocation.getMethod().getReturnType() == String.class
                    ? "message"
                    : RETURNS_DEFAULTS.answer(invocation));
            this.dataStore.claims = new ArrayList<>();
            doReturn(new PlayerData()).when(this.dataStore).getPlayerData(OWNER_ID);
            PlayerData recipientData = new PlayerData();
            recipientData.playerID = RECIPIENT_ID;
            recipientData.setAccruedClaimBlocks(recipientAccruedBlocks);
            recipientData.setBonusClaimBlocks(0);
            doReturn(recipientData).when(this.dataStore).getPlayerData(RECIPIENT_ID);
            doReturn(this.claim).when(this.dataStore).getClaimAt(any(Location.class), anyBoolean(), any());
            doAnswer(invocation -> {
                this.claim.ownerID = invocation.getArgument(1);
                return true;
            }).when(this.dataStore).tryChangeClaimOwner(eq(this.claim), any());

            OfflinePlayer recipient = mock(OfflinePlayer.class);
            when(recipient.getUniqueId()).thenReturn(RECIPIENT_ID);
            when(recipient.getName()).thenReturn(RECIPIENT_NAME);
            when(server.getOfflinePlayer(RECIPIENT_ID)).thenReturn(recipient);
            GriefPrevention.playerNameToIDMap.put(RECIPIENT_NAME, RECIPIENT_ID);

            this.plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
            doReturn(server).when(this.plugin).getServer();
            this.plugin.dataStore = this.dataStore;
            this.plugin.config_claims_transferClaimEnabled = true;
            this.plugin.config_claims_transferClaimPrice = 0.0;
            GriefPrevention.instance = this.plugin;
        }

        private boolean transfer(String... args) {
            return this.plugin.handleTransferClaimCommand(this.owner, args);
        }

        private void cancelTransfers() {
            doReturn(false).when(this.dataStore).tryChangeClaimOwner(eq(this.claim), any());
        }
    }
}
