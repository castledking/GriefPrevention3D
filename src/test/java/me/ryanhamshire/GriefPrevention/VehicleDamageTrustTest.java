package me.ryanhamshire.GriefPrevention;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Breaking a vehicle in a claim takes build trust, not the container trust that opening a chest
 * minecart takes.
 */
class VehicleDamageTrustTest {
    private static final UUID PLAYER_UUID = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739");

    @AfterEach
    void clearPlugin() {
        GriefPrevention.instance = null;
    }

    @Test
    void breakingAVehicleIsDeniedWithoutBuildTrust() {
        Fixture fixture = new Fixture();
        when(fixture.claim.checkPermission(eq(fixture.player), eq(ClaimPermission.Build), eq(fixture.event), any()))
                .thenReturn(() -> "no build trust here");

        fixture.handler.onVehicleDamage(fixture.event);

        verify(fixture.event).setCancelled(true);
    }

    @Test
    void containerTrustAloneDoesNotAllowBreakingAVehicle() {
        // A claim with public container trust - a shop or a loot warp - would otherwise let any
        // visitor break its minecarts.
        Fixture fixture = new Fixture();
        when(fixture.claim.checkPermission(eq(fixture.player), eq(ClaimPermission.Container), eq(fixture.event), any()))
                .thenReturn(null);
        when(fixture.claim.checkPermission(eq(fixture.player), eq(ClaimPermission.Build), eq(fixture.event), any()))
                .thenReturn(() -> "no build trust here");

        fixture.handler.onVehicleDamage(fixture.event);

        verify(fixture.event).setCancelled(true);
    }

    @Test
    void buildTrustAllowsBreakingAVehicle() {
        Fixture fixture = new Fixture();
        when(fixture.claim.checkPermission(eq(fixture.player), eq(ClaimPermission.Build), eq(fixture.event), any()))
                .thenReturn(null);

        fixture.handler.onVehicleDamage(fixture.event);

        verify(fixture.event, never()).setCancelled(true);
    }

    private static final class Fixture {
        private final Claim claim = mock(Claim.class);
        private final Player player = mock(Player.class);
        private final VehicleDamageEvent event = mock(VehicleDamageEvent.class);
        private final EntityDamageHandler handler;

        @SuppressWarnings("unchecked")
        private Fixture() {
            GriefPrevention plugin = mock(GriefPrevention.class);
            plugin.config_claims_preventTheft = true;
            GriefPrevention.instance = plugin;
            DataStore dataStore = mock(DataStore.class);

            World world = mock(World.class);
            when(plugin.claimsEnabledForWorld(world)).thenReturn(true);

            Location vehicleLocation = mock(Location.class);
            Vehicle vehicle = mock(Vehicle.class);
            when(vehicle.getWorld()).thenReturn(world);
            when(vehicle.getLocation()).thenReturn(vehicleLocation);

            when(player.getUniqueId()).thenReturn(PLAYER_UUID);
            PlayerData playerData = new PlayerData();
            when(dataStore.getPlayerData(PLAYER_UUID)).thenReturn(playerData);
            when(dataStore.getClaimAt(vehicleLocation, false, null)).thenReturn(claim);
            when(claim.getOwnerName()).thenReturn("someone");

            when(event.getVehicle()).thenReturn(vehicle);
            when(event.getAttacker()).thenReturn(player);

            // Unstubbed permission checks deny, matching a player with no trust at all.
            Supplier<String> denied = () -> "no trust here";
            when(claim.checkPermission(any(Player.class), any(ClaimPermission.class), any(), any()))
                    .thenReturn(denied);

            handler = new EntityDamageHandler(dataStore, plugin);
        }
    }
}
