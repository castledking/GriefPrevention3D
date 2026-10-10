package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.Collections;
import java.util.UUID;
import java.util.Vector;
import me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link DataStore#tryChangeClaimOwner} reports whether a listener cancelled the transfer. */
@SuppressWarnings("null")
class DataStoreClaimTransferTest
{
    private static Server server;

    @BeforeAll
    static void beforeAll()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void anAllowedTransferChangesTheOwner()
    {
        UUID newOwner = UUID.randomUUID();
        Claim claim = adminClaim();
        DataStore dataStore = dataStore(newOwner);
        listenToTransfer(false);

        assertTrue(dataStore.tryChangeClaimOwner(claim, newOwner));

        assertEquals(newOwner, claim.getOwnerID());
    }

    @Test
    void aCancelledTransferChangesNothing()
    {
        UUID newOwner = UUID.randomUUID();
        Claim claim = adminClaim();
        DataStore dataStore = dataStore(newOwner);
        listenToTransfer(true);

        assertFalse(dataStore.tryChangeClaimOwner(claim, newOwner));

        assertNull(claim.getOwnerID());
    }

    /** An admin claim with no owner yet, and a datastore whose bookkeeping does nothing. */
    private static DataStore dataStore(UUID newOwner)
    {
        DataStore dataStore = mock(DataStore.class, CALLS_REAL_METHODS);
        doNothing().when(dataStore).saveClaim(any());
        PlayerData newOwnerData = mock(PlayerData.class);
        when(newOwnerData.getClaims()).thenReturn(new Vector<Claim>());
        doReturn(newOwnerData).when(dataStore).getPlayerData(newOwner);
        return dataStore;
    }

    private static Claim adminClaim()
    {
        return new Claim(
            new Location(null, 0, 0, 0),
            new Location(null, 9, 64, 9),
            null,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            1L);
    }

    private static void listenToTransfer(boolean cancel)
    {
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            invocation.<ClaimTransferEvent>getArgument(0).setCancelled(cancel);
            return null;
        }).when(pluginManager).callEvent(any(ClaimTransferEvent.class));
        when(server.getPluginManager()).thenReturn(pluginManager);
    }
}
