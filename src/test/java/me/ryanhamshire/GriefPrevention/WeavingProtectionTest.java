package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class WeavingProtectionTest {
    private static final UUID PLAYER_ID = UUID.fromString("b7c2e4a1-5d3f-4b8e-a6c9-1e2d3f4a5b6c");
    private static final UUID OWNER_ID = UUID.fromString("c7c2e4a1-5d3f-4b8e-a6c9-1e2d3f4a5b6c");
    private static Server server;

    @BeforeAll
    static void beforeAll() {
        server = ServerMocks.newServer();
        doReturn(mock(PluginManager.class)).when(server).getPluginManager();
        OfflinePlayer owner = mock(OfflinePlayer.class);
        when(owner.getName()).thenReturn("Claim owner");
        when(owner.getUniqueId()).thenReturn(OWNER_ID);
        when(server.getOfflinePlayer(OWNER_ID)).thenReturn(owner);
        Bukkit.setServer(server);
        when(PotionEffectType.WEAVING.getCategory()).thenReturn(PotionEffectTypeCategory.HARMFUL);
        when(PotionEffectType.REGENERATION.getCategory()).thenReturn(PotionEffectTypeCategory.BENEFICIAL);
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"PLAYER", "COW", "ZOMBIE", "ENDERMAN"})
    void unauthorizedWeavingCannotPlaceWebsInClaims(EntityType type) {
        Fixture f = new Fixture();
        assertTrue(f.placeWeb(f.entity(type, f.inside), f.inside).isCancelled());
    }

    @Test
    void playerDyingOutsideCannotPlaceWebsAcrossClaimBoundary() {
        Fixture f = new Fixture();
        when(f.player.getLocation()).thenReturn(new Location(f.world, -1, 64, 5));
        assertTrue(f.placeWeb(f.player, new Location(f.world, 0, 64, 5)).isCancelled());
    }

    @Test
    void buildTrustedPlayerMayPlaceWebs() {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Build);
        assertFalse(f.placeWeb(f.player, f.inside).isCancelled());
    }

    @Test
    void containerTrustDoesNotPermitPlacingWebs() {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Container);
        assertTrue(f.placeWeb(f.player, f.inside).isCancelled());
    }

    @Test
    void authorizedPlayerMayPlaceWebsFromOutsideTheClaim() {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Build);
        when(f.player.getLocation()).thenReturn(new Location(f.world, -1, 64, 5));
        assertFalse(f.placeWeb(f.player, new Location(f.world, 0, 64, 5)).isCancelled());
    }

    @Test
    void ignoreClaimsStillPermitsPlayerWeaving() {
        Fixture f = new Fixture();
        f.playerData.ignoreClaims = true;
        when(f.player.hasPermission("griefprevention.ignoreclaims")).thenReturn(true);
        assertFalse(f.placeWeb(f.player, f.inside).isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"PLAYER", "COW", "ZOMBIE", "ENDERMAN"})
    void weavingStillWorksInSurvivalWilderness(EntityType type) {
        Fixture f = new Fixture();
        Location outside = new Location(f.world, -1, 64, 5);
        assertFalse(f.placeWeb(f.entity(type, f.inside), outside).isCancelled());
    }

    @Test
    void mobWebsRespectTheVerticalBoundaryOf3DClaims() {
        Fixture f = new Fixture();
        LivingEntity cow = f.entity(EntityType.COW, f.inside);
        assertTrue(f.placeWeb(cow, new Location(f.world, 5, 70, 5)).isCancelled());
        assertFalse(f.placeWeb(cow, new Location(f.world, 5, 71, 5)).isCancelled());
    }

    @Test
    void restrictedSubdivisionDoesNotInheritPermissionToPlaceWebs() {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Build);
        f.subdivision(true);
        assertTrue(f.placeWeb(f.player, f.inside).isCancelled());
    }

    @Test
    void inheritingSubdivisionAllowsItsParentsBuildersToPlaceWebs() {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Build);
        f.subdivision(false);
        assertFalse(f.placeWeb(f.player, f.inside).isCancelled());
    }

    @Test
    void playerCannotUseWeavingToBuildInWildernessWhereClaimsAreRequired() {
        Fixture f = new Fixture();
        f.plugin.config_claims_worldModes.put(f.world, ClaimsMode.SurvivalRequiringClaims);
        assertTrue(f.placeWeb(f.player, new Location(f.world, -1, 64, 5)).isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"PLAYER", "COW", "ZOMBIE", "ENDERMAN"})
    void weavingIsUnrestrictedWhenClaimsAreDisabled(EntityType type) {
        Fixture f = new Fixture();
        when(f.plugin.claimsEnabledForWorld(f.world)).thenReturn(false);
        f.plugin.config_claims_worldModes.put(f.world, ClaimsMode.Disabled);
        assertFalse(f.placeWeb(f.entity(type, f.inside), f.inside).isCancelled());
    }

    @Test
    void ordinaryEndermanBlockChangesRemainProtected() {
        Fixture f = new Fixture();
        EntityChangeBlockEvent event = f.changeBlock(f.entity(EntityType.ENDERMAN, f.inside), f.inside, Material.AIR);
        f.dispatch(event);
        assertTrue(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void untrustedPlayerCannotApplyWeavingToClaimedAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        assertFalse(f.applyPotion(route, f.player, f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void accessTrustDoesNotPermitWeavingOnClaimedAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Access);
        assertFalse(f.applyPotion(route, f.player, f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void villagersAreAlsoProtectedFromWeaving(PotionRoute route) {
        Fixture f = new Fixture();
        assertFalse(f.applyPotion(route, f.player, f.entity(EntityType.VILLAGER, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void containerTrustedPlayerMayApplyWeavingToClaimedAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        f.claim.setPermission(PLAYER_ID.toString(), ClaimPermission.Container);
        assertTrue(f.applyPotion(route, f.player, f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void dispenserOutsideClaimCannotApplyWeavingToClaimedAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        assertFalse(f.applyPotion(route, f.dispenser(new Location(f.world, -1, 64, 5)),
                f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void dispenserInSameClaimMayApplyWeavingToItsAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        assertTrue(f.applyPotion(route, f.dispenser(f.inside), f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(value = PotionRoute.class, names = {"LINGERING_CUSTOM", "LINGERING_BASE"})
    void sourcelessLingeringWeavingCannotAffectClaimedAnimals(PotionRoute route) {
        Fixture f = new Fixture();
        assertFalse(f.applyPotion(route, null, f.entity(EntityType.COW, f.inside), PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void weavingMayStillAffectTheThrower(PotionRoute route) {
        Fixture f = new Fixture();
        assertTrue(f.applyPotion(route, f.player, f.player, PotionEffectType.WEAVING));
    }

    @ParameterizedTest
    @EnumSource(PotionRoute.class)
    void beneficialPotionsAreUnaffected(PotionRoute route) {
        Fixture f = new Fixture();
        assertTrue(f.applyPotion(route, f.player, f.entity(EntityType.COW, f.inside), PotionEffectType.REGENERATION));
    }

    private enum PotionRoute { SPLASH, LINGERING_CUSTOM, LINGERING_BASE }

    private static final class Fixture {
        final GriefPrevention plugin = mock(GriefPrevention.class);
        final DataStore store = mock(DataStore.class);
        final World world = mock(World.class);
        final Location inside = new Location(world, 5, 64, 5);
        final Player player = mock(Player.class);
        final PlayerData playerData = new PlayerData();
        final List<Claim> claims = new ArrayList<>();
        final Claim claim;

        Fixture() {
            GriefPrevention.instance = plugin;
            plugin.dataStore = store;
            plugin.config_claims_worldModes = new ConcurrentHashMap<>();
            plugin.config_claims_worldModes.put(world, ClaimsMode.Survival);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
            when(store.getPlayerData(PLAYER_ID)).thenReturn(playerData);
            when(store.getMessage(eq(Messages.NoDamageClaimedEntity), anyString())).thenReturn("No permission");
            when(player.getUniqueId()).thenReturn(PLAYER_ID);
            when(player.getType()).thenReturn(EntityType.PLAYER);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(inside);
            when(server.getPlayer(PLAYER_ID)).thenReturn(player);
            claim = claim(0, 9, false, 1L);
            claims.add(claim);
            when(store.getClaimAt(any(Location.class), eq(false), any())).thenAnswer(invocation -> {
                Location location = invocation.getArgument(0);
                for (Claim candidate : claims) {
                    if (candidate.contains(location, false, false)) return candidate;
                }
                return null;
            });
        }

        Claim claim(int min, int max, boolean restricted, long id) {
            return new Claim(new Location(world, min, 60, min), new Location(world, max, 70, max),
                    OWNER_ID, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), restricted, id, true);
        }

        void subdivision(boolean restricted) {
            Claim child = claim(2, 7, restricted, 2L);
            child.parent = claim;
            claim.children.add(child);
            claims.add(0, child);
        }

        LivingEntity entity(EntityType type, Location location) {
            if (type == EntityType.PLAYER) return player;
            LivingEntity entity;
            switch (type) {
                case COW: entity = mock(Cow.class); break;
                case ZOMBIE: entity = mock(Zombie.class); break;
                case ENDERMAN: entity = mock(Enderman.class); break;
                case VILLAGER: entity = mock(Villager.class); break;
                default: throw new AssertionError(type);
            }
            when(entity.getType()).thenReturn(type);
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenReturn(location);
            return entity;
        }

        Block block(Location location) {
            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenReturn(location);
            when(block.getType()).thenReturn(Material.AIR);
            return block;
        }

        EntityChangeBlockEvent changeBlock(LivingEntity entity, Location location, Material material) {
            Block block = block(location);
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(material);
            return new EntityChangeBlockEvent(entity, block, data);
        }

        EntityChangeBlockEvent placeWeb(LivingEntity entity, Location location) {
            EntityChangeBlockEvent event = changeBlock(entity, location, Material.COBWEB);
            dispatch(event);
            return event;
        }

        void dispatch(EntityChangeBlockEvent event) {
            EntityEventHandler handler = new EntityEventHandler(store, plugin);
            handler.onEntityChangeBLock(event);
            handler.onEntityPickup(event);
            // Replay the archaeology listener too; it must leave weaving events alone.
            mock(PlayerEventHandler.class, CALLS_REAL_METHODS).onPlayerBrushComplete(event);
        }

        BlockProjectileSource dispenser(Location location) {
            BlockProjectileSource source = mock(BlockProjectileSource.class);
            Block block = block(location);
            when(source.getBlock()).thenReturn(block);
            return source;
        }

        boolean applyPotion(PotionRoute route, ProjectileSource source, LivingEntity target, PotionEffectType type) {
            PotionEffect effect = mock(PotionEffect.class);
            when(effect.getType()).thenReturn(type);
            EntityDamageHandler handler = new EntityDamageHandler(store, plugin);
            if (route == PotionRoute.SPLASH) {
                ThrownPotion potion = mock(ThrownPotion.class);
                when(potion.getShooter()).thenReturn(source);
                when(potion.getEffects()).thenReturn(Collections.singletonList(effect));
                Map<LivingEntity, Double> affected = new HashMap<>();
                affected.put(target, 1.0);
                PotionSplashEvent event = new PotionSplashEvent(potion, null, null, null, affected);
                handler.onPotionSplash(event);
                return event.getIntensity(target) > 0;
            }

            AreaEffectCloud cloud = mock(AreaEffectCloud.class);
            when(cloud.getSource()).thenReturn(source);
            if (route == PotionRoute.LINGERING_BASE) {
                when(cloud.getBasePotionType()).thenReturn(type == PotionEffectType.WEAVING
                        ? PotionType.WEAVING : PotionType.REGENERATION);
            } else {
                when(cloud.getCustomEffects()).thenReturn(Collections.singletonList(effect));
            }
            List<LivingEntity> affected = new ArrayList<>();
            affected.add(target);
            AreaEffectCloudApplyEvent event = new AreaEffectCloudApplyEvent(cloud, affected);
            new AreaEffectCloudApplyEventHandler(handler).onAreaEffectCloudApply(event);
            return event.getAffectedEntities().contains(target);
        }
    }
}
