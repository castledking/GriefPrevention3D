package me.ryanhamshire.GriefPrevention;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Leashing a boat out of a claim is theft, so it takes container trust, as leashing a claimed
 * creature does. Upstream GriefPrevention only checks creatures, so any player can drag away a
 * plain boat there.
 */
@SuppressWarnings("null")
class BoatLeashTheftTest {

    private static final UUID PLAYER_ID = UUID.fromString("fc70e25f-f8da-4f33-bb9c-ff0db586cd30");

    @BeforeAll
    static void beforeAll() {
        Bukkit.setServer(ServerMocks.newServer());
        InventoryType.values();
        //noinspection ResultOfMethodCallIgnored
        PlayerEventHandler.class.getName();
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void anUntrustedPlayerCannotLeashAClaimedBoat() {
        Fixture fixture = new Fixture(true, false);

        fixture.handler.onPlayerLeashEntity(fixture.event);

        verify(fixture.event).setCancelled(true);
    }

    @Test
    void aTrustedPlayerMayLeashAClaimedBoat() {
        Fixture fixture = new Fixture(true, true);

        fixture.handler.onPlayerLeashEntity(fixture.event);

        verify(fixture.event, never()).setCancelled(true);
    }

    @Test
    void boatsAreFreeToLeashWhenTheftProtectionIsOff() {
        Fixture fixture = new Fixture(false, false);

        fixture.handler.onPlayerLeashEntity(fixture.event);

        verify(fixture.event, never()).setCancelled(true);
    }

    private static final class Fixture {
        private final PlayerLeashEntityEvent event = mock(PlayerLeashEntityEvent.class);
        private final PlayerEventHandler handler;

        private Fixture(boolean preventTheft, boolean trusted) {
            DataStore dataStore = mock(DataStore.class);
            when(dataStore.loadBannedWords()).thenReturn(Collections.emptyList());
            when(dataStore.getPlayerData(PLAYER_ID)).thenReturn(new PlayerData());

            GriefPrevention plugin = mock(GriefPrevention.class);
            plugin.dataStore = dataStore;
            plugin.config_claims_preventTheft = preventTheft;
            plugin.config_pvp_blockedCommands = new ArrayList<>();
            plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
            plugin.config_spam_monitorSlashCommands = new ArrayList<>();
            plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            GriefPrevention.instance = plugin;

            World world = mock(World.class);
            when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
            Location location = new Location(world, 5, 64, 5);

            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(PLAYER_ID);

            Boat boat = mock(Boat.class);
            when(boat.getType()).thenReturn(EntityType.valueOf(boatTypeName()));
            when(boat.getWorld()).thenReturn(world);
            when(boat.getLocation()).thenReturn(location);

            Claim claim = mock(Claim.class);
            when(dataStore.getClaimAt(eq(location), eq(false), any())).thenReturn(claim);
            when(claim.checkPermission(eq(player), eq(ClaimPermission.Container), any()))
                    .thenReturn(trusted ? null : () -> "no container trust here");

            when(this.event.getPlayer()).thenReturn(player);
            when(this.event.getEntity()).thenReturn(boat);

            this.handler = new PlayerEventHandler(dataStore, plugin);
        }

        /** Boats became one entity type per wood in 1.21.2; any of them will do. */
        private static String boatTypeName() {
            for (EntityType type : EntityType.values()) {
                if (type.name().contains("BOAT")) {
                    return type.name();
                }
            }
            throw new IllegalStateException("No boat entity type");
        }
    }
}
