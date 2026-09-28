package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.griefprevention.test.ServerMocks;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Animals in a claim may not be used to get around its protection: a pet needs its owner's trust to
 * attack claimed animals (GriefPrevention#2642), and a polar bear guarding cubs may not attack a
 * player who is not allowed to hit it back.
 */
@SuppressWarnings("null")
class ClaimedAnimalAggressionTest {

    private static final UUID OWNER_ID = UUID.fromString("b7c2e4a1-5d3f-4b8e-a6c9-1e2d3f4a5b6c");

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
    void anUntrustedOwnersWolfCannotTargetAClaimedCow() {
        Fixture fixture = new Fixture(false);
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(
                fixture.wolf, fixture.cow, EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET);

        fixture.handler.onEntityTargetLivingEntity(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void anUntrustedOwnersWolfCannotHurtAClaimedCow() {
        Fixture fixture = new Fixture(false);
        EntityDamageByEntityEvent event = damage(fixture.wolf, fixture.cow);

        fixture.handler.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(fixture.wolf).setTarget(null);
    }

    @Test
    void aTrustedOwnersWolfMayHuntInTheClaim() {
        Fixture fixture = new Fixture(true);
        EntityTargetLivingEntityEvent target = new EntityTargetLivingEntityEvent(
                fixture.wolf, fixture.cow, EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET);
        EntityDamageByEntityEvent damage = damage(fixture.wolf, fixture.cow);

        fixture.handler.onEntityTargetLivingEntity(target);
        fixture.handler.onEntityDamage(damage);

        assertFalse(target.isCancelled());
        verify(damage, never()).setCancelled(true);
    }

    @Test
    void aPolarBearGuardingCubsCannotTurnOnAnUntrustedPlayer() {
        Fixture fixture = new Fixture(false);
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(
                fixture.bear, fixture.owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        fixture.handler.onEntityTargetLivingEntity(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aPolarBearCannotMaulAnUntrustedPlayer() {
        Fixture fixture = new Fixture(false);
        EntityDamageByEntityEvent event = damage(fixture.bear, fixture.owner);

        fixture.handler.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(fixture.bear).setTarget(null);
    }

    @Test
    void aPolarBearStillDefendsItselfAgainstTrustedPlayers() {
        Fixture fixture = new Fixture(true);
        EntityTargetLivingEntityEvent cubs = new EntityTargetLivingEntityEvent(
                fixture.bear, fixture.owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);

        fixture.handler.onEntityTargetLivingEntity(cubs);

        assertFalse(cubs.isCancelled());
    }

    @Test
    void aPolarBearMayRetaliate() {
        Fixture fixture = new Fixture(false);
        EntityTargetLivingEntityEvent retaliation = new EntityTargetLivingEntityEvent(
                fixture.bear, fixture.owner, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);

        fixture.handler.onEntityTargetLivingEntity(retaliation);

        assertFalse(retaliation.isCancelled());
    }

    private static EntityDamageByEntityEvent damage(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity damaged) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(damaged);
        when(event.getDamager()).thenReturn(damager);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        return event;
    }

    /** A cow and a polar bear stand in a claim; the wolf belongs to the online player. */
    private static final class Fixture {
        private final Player owner = mock(Player.class);
        private final Wolf wolf = mock(Wolf.class);
        private final Cow cow = mock(Cow.class);
        private final PolarBear bear = mock(PolarBear.class);
        private final EntityDamageHandler handler;

        private Fixture(boolean ownerTrusted) {
            GriefPrevention plugin = mock(GriefPrevention.class);
            plugin.config_claims_protectCreatures = true;
            plugin.config_claims_protectHorses = true;
            plugin.config_claims_protectDonkeys = true;
            plugin.config_claims_protectLlamas = true;
            GriefPrevention.instance = plugin;

            World world = mock(World.class);
            when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
            Location claimed = new Location(world, 5, 64, 5);

            when(this.owner.getUniqueId()).thenReturn(OWNER_ID);
            when(this.owner.getType()).thenReturn(EntityType.PLAYER);
            when(this.owner.getWorld()).thenReturn(world);
            when(this.owner.getLocation()).thenReturn(claimed);
            when(server.getPlayer(OWNER_ID)).thenReturn(this.owner);

            when(this.wolf.isTamed()).thenReturn(true);
            when(this.wolf.getOwner()).thenReturn(this.owner);
            when(this.wolf.getType()).thenReturn(EntityType.WOLF);
            when(this.wolf.getWorld()).thenReturn(world);
            when(this.wolf.getLocation()).thenReturn(claimed);

            when(this.cow.getType()).thenReturn(EntityType.COW);
            when(this.cow.getWorld()).thenReturn(world);
            when(this.cow.getLocation()).thenReturn(claimed);

            when(this.bear.getType()).thenReturn(EntityType.POLAR_BEAR);
            when(this.bear.getWorld()).thenReturn(world);
            when(this.bear.getLocation()).thenReturn(claimed);

            Claim claim = mock(Claim.class);
            when(claim.getOwnerName()).thenReturn("someone");
            when(claim.checkPermission(any(Player.class), any(ClaimPermission.class), any()))
                    .thenReturn(() -> "no trust here");
            when(claim.checkPermission(any(Player.class), any(ClaimPermission.class), any(), any()))
                    .thenReturn(() -> "no trust here");
            if (ownerTrusted) {
                when(claim.checkPermission(eq(this.owner), eq(ClaimPermission.Container), isNull()))
                        .thenReturn(null);
            }

            DataStore dataStore = mock(DataStore.class);
            when(dataStore.getPlayerData(OWNER_ID)).thenReturn(new PlayerData());
            when(dataStore.getClaimAt(any(Location.class), eq(false), any())).thenReturn(claim);

            this.handler = new EntityDamageHandler(dataStore, plugin);
        }
    }
}
