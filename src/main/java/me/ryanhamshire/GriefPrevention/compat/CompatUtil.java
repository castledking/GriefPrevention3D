package me.ryanhamshire.GriefPrevention.compat;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Statistic;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Compatibility utility for bridging API differences between Minecraft versions.
 * Provides safe access to APIs that may not exist in 1.8.8 Bukkit.
 */
public class CompatUtil {

    /**
     * Get the hand used in a PlayerInteractEvent (1.9+)
     * Returns null if method doesn't exist (pre-1.9)
     */
    public static EquipmentSlot getInteractEventHand(PlayerInteractEvent event) {
        try {
            return event.getHand();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Pre-1.9: assume HAND (main hand)
            return null;
        }
    }

    /**
     * Get the hand used in a PlayerInteractEntityEvent (1.9+)
     * Returns null if method doesn't exist (pre-1.9)
     */
    public static EquipmentSlot getInteractEntityEventHand(PlayerInteractEntityEvent event) {
        try {
            return event.getHand();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Pre-1.9: assume HAND (main hand)
            return null;
        }
    }

    /**
     * Get item in main hand safely (1.9+)
     * Falls back to getItemInHand() for 1.8.8
     */
    public static ItemStack getItemInMainHand(Player player) {
        try {
            // Try 1.9+ method first
            return player.getInventory().getItemInMainHand();
        } catch (NoSuchMethodError e) {
            // Fallback to 1.8.8 method
            return player.getItemInHand();
        }
    }

    /**
     * Get item in off hand safely (1.9+)
     * Returns null for 1.8.8 (no off-hand support)
     */
    public static ItemStack getItemInOffHand(Player player) {
        try {
            return player.getInventory().getItemInOffHand();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // 1.8.8 doesn't have off-hand
            return null;
        }
    }

    /**
     * Check if material is GRASS_BLOCK (1.13+)
     * Fallback to GRASS (pre-1.13)
     */
    public static boolean isGrassBlock(Material material) {
        try {
            Material grassBlock = Material.valueOf("GRASS_BLOCK");
            return material == grassBlock;
        } catch (IllegalArgumentException e) {
            // 1.8.8: use GRASS
            try {
                Material grass = Material.valueOf("GRASS");
                return material == grass;
            } catch (IllegalArgumentException e2) {
                return false;
            }
        }
    }

    /**
     * Get GRASS_BLOCK material safely (1.13+)
     * Fallback to GRASS for 1.8.8
     */
    public static Material getGrassBlockMaterial() {
        try {
            return Material.valueOf("GRASS_BLOCK");
        } catch (IllegalArgumentException e) {
            // 1.8.8: use GRASS
            try {
                return Material.valueOf("GRASS");
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    /**
     * Get END_PORTAL_FRAME material safely (1.13+)
     * Fallback to ENDER_PORTAL_FRAME for 1.8.8
     */
    public static Material getEndPortalFrameMaterial() {
        try {
            return Material.valueOf("END_PORTAL_FRAME");
        } catch (IllegalArgumentException e) {
            // 1.8.8 uses ENDER_PORTAL_FRAME
            try {
                return Material.valueOf("ENDER_PORTAL_FRAME");
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    /**
     * Check if BlockIgniteEvent cause is ARROW (1.11+)
     */
    public static boolean isArrowIgniteCause(BlockIgniteEvent.IgniteCause cause) {
        try {
            return cause == BlockIgniteEvent.IgniteCause.ARROW;
        } catch (NoSuchFieldError e) {
            // 1.8.8: ARROW cause doesn't exist, ignore
            return false;
        }
    }

    /**
     * Get ARROW ignite cause safely (1.11+)
     * Returns null if not available
     */
    public static BlockIgniteEvent.IgniteCause getArrowIgniteCause() {
        try {
            return BlockIgniteEvent.IgniteCause.ARROW;
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    /**
     * Check if statistic is PICKUP (1.13+)
     */
    public static boolean canUsePickupStatistic() {
        try {
            Statistic stat = Statistic.PICKUP;
            return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
    }

    /**
     * Get PICKUP statistic safely (1.13+)
     * Returns null if not available
     */
    public static Statistic getPickupStatistic() {
        try {
            return Statistic.PICKUP;
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    /**
     * Check if EquipmentSlot.OFF_HAND exists (1.9+)
     */
    public static boolean hasOffHandEquipmentSlot() {
        try {
            EquipmentSlot slot = EquipmentSlot.OFF_HAND;
            return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
    }

    /**
     * Check if PlayerTeleportEvent has CHORUS_FRUIT cause (1.9+)
     */
    public static boolean hasChorusFruitTeleportCause() {
        try {
            PlayerTeleportEvent.TeleportCause cause = PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
            return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
    }

    /**
     * Get CHORUS_FRUIT teleport cause safely (1.9+)
     * Returns null if not available
     */
    public static PlayerTeleportEvent.TeleportCause getChorusFruitCause() {
        try {
            return PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    /**
     * Check if WorldBorder.isInside() method exists (1.11+)
     */
    public static boolean hasWorldBorderIsInside() {
        try {
            // Try to access the method via reflection
            WorldBorder wb = null; // We can't instantiate, but we can check if method exists
            WorldBorder.class.getMethod("isInside", Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Check if location is inside world border safely (1.11+)
     * Returns true for 1.8.8 (no border support, assume inside)
     */
    public static boolean isInsideWorldBorder(WorldBorder border, Location location) {
        try {
            return border.isInside(location);
        } catch (NoSuchMethodError e) {
            // 1.8.8 doesn't have this method, assume inside
            return true;
        }
    }

    /**
     * Check if class exists (for entity types like Donkey)
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Safe entity type check that doesn't throw NoClassDefFoundError
     */
    public static boolean canCheckEntityType(String entityClassName) {
        return classExists("org.bukkit.entity." + entityClassName);
    }

    /**
     * Set portal cooldown safely (1.13+)
     * Does nothing on 1.8.8
     */
    public static void setPortalCooldown(Player player, int ticks) {
        try {
            player.setPortalCooldown(ticks);
        } catch (NoSuchMethodError e) {
            // 1.8.8: setPortalCooldown doesn't exist
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * Check whether a material is a lead. The item was renamed from LEASH to LEAD in 1.13,
     * so neither constant can be named directly without risking a NoSuchFieldError.
     */
    public static boolean isLead(Material material) {
        String name = material.name();
        return name.equals("LEAD") || name.equals("LEASH");
    }

    private static final Class<?> MOB_CLASS = findClass("org.bukkit.entity.Mob");
    private static final Class<?> ABSTRACT_HORSE_CLASS = findClass("org.bukkit.entity.AbstractHorse");
    private static final Class<?> LOOTABLE_CLASS = findClass("org.bukkit.loot.Lootable");

    /**
     * Check whether an entity is a mob (1.16+ Mob interface).
     * Pre-1.16 approximates it as any living entity that isn't a player.
     */
    public static boolean isMob(Object entity) {
        if (MOB_CLASS != null) return MOB_CLASS.isInstance(entity);
        return entity instanceof LivingEntity && !(entity instanceof Player);
    }

    /**
     * Check whether an entity is a horse of any variant (1.11+ AbstractHorse).
     * Pre-1.11 all variants, donkeys and mules included, are plain Horse entities.
     */
    public static boolean isAbstractHorse(Object entity) {
        if (ABSTRACT_HORSE_CLASS != null) return ABSTRACT_HORSE_CLASS.isInstance(entity);
        return entity instanceof org.bukkit.entity.Horse;
    }

    /**
     * Check whether a block state is lootable but carries no loot table (1.10+).
     * Returns false pre-1.10, where loot tables aren't exposed at all.
     */
    public static boolean isLootableWithoutLootTable(Object state) {
        if (LOOTABLE_CLASS == null || !LOOTABLE_CLASS.isInstance(state)) return false;

        try {
            return LOOTABLE_CLASS.getMethod("getLootTable").invoke(state) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Check whether an entity is of a named type, without naming its class.
     * An {@code instanceof} against an entity interface that doesn't exist on the running
     * server throws NoClassDefFoundError when the check executes, so newer entity types have
     * to be matched by name instead.
     */
    public static boolean isEntityType(Entity entity, String entityTypeName) {
        return entity != null && entity.getType().name().equals(entityTypeName);
    }

    /**
     * Safely check if an EntityType matches a given name (for newer entity types)
     * Returns false if entity type doesn't exist in this version
     */
    public static boolean isEntityType(EntityType entityType, String entityTypeName) {
        return entityType != null && entityType.name().equals(entityTypeName);
    }

    /**
     * Safely check if a material matches a given name (for newer materials)
     * Returns false if material doesn't exist in this version
     */
    public static boolean isMaterial(Material material, String materialName) {
        // Compared by name rather than valueOf: identical result, but no exception is thrown
        // per call on versions where the material doesn't exist. This runs in hot event paths.
        return material != null && material.name().equals(materialName);
    }

    /**
     * Update player commands (1.13+ only)
     */
    public static void updateCommands(Player player) {
        try {
            player.getClass().getMethod("updateCommands").invoke(player);
        } catch (ReflectiveOperationException | LinkageError e) {
            // 1.8.8: updateCommands doesn't exist
        }
    }

    /**
     * Check if EntityExplodeEvent has getExplosionResult() method (1.13+)
     */
    public static boolean hasExplosionResult() {
        try {
            Class.forName("org.bukkit.ExplosionResult");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get explosion result safely (1.13+)
     * Returns null if not available (assumes normal explosion)
     */
    public static Object getExplosionResult(org.bukkit.event.entity.EntityExplodeEvent event) {
        try {
            return event.getExplosionResult();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // 1.8.8: getExplosionResult doesn't exist
            return null;
        }
    }

    /**
     * Get explosion result safely for BlockExplodeEvent (1.13+)
     * Returns null if not available (assumes normal explosion)
     */
    public static Object getExplosionResult(org.bukkit.event.block.BlockExplodeEvent event) {
        try {
            return event.getExplosionResult();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // 1.8.8: getExplosionResult doesn't exist
            return null;
        }
    }

    /**
     * Check if explosion result is TRIGGER_BLOCK (1.13+)
     * Returns false for 1.8.8 (assumes normal explosion)
     */
    public static boolean isTriggerBlockExplosion(Object explosionResult) {
        if (explosionResult == null) {
            return false;
        }
        try {
            Class<?> explosionResultClass = Class.forName("org.bukkit.ExplosionResult");
            Object triggerBlock = explosionResultClass.getField("TRIGGER_BLOCK").get(null);
            return explosionResult.equals(triggerBlock);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }

    /**
     * Get a BlockFace's unit direction vector (1.13+).
     * Computed from the face's mod values rather than calling BlockFace#getDirection, which
     * doesn't exist pre-1.13. This is the same calculation the modern API performs.
     */
    public static Vector getDirection(BlockFace face) {
        Vector direction = new Vector(face.getModX(), face.getModY(), face.getModZ());
        if (face.getModX() != 0 || face.getModY() != 0 || face.getModZ() != 0) {
            direction.normalize();
        }
        return direction;
    }

    private static final boolean HAS_MATERIAL_IS_AIR = resolveMethod(Material.class, "isAir") != null;

    /**
     * Check whether a material is an air type (1.13+).
     * Pre-1.13 has no Material#isAir and only a single AIR type.
     */
    public static boolean isAir(Material material) {
        if (HAS_MATERIAL_IS_AIR) {
            return material.isAir();
        }
        return material == Material.AIR || material.name().endsWith("_AIR");
    }

    private static final Method PLAYER_GET_LOCALE = resolveMethod(Player.class, "getLocale");
    private static final Method SPIGOT_GET_LOCALE = resolveMethod(Player.Spigot.class, "getLocale");

    private static Method resolveMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    /**
     * Get the player's client locale (e.g. "en_us") safely.
     * Player#getLocale() only exists on 1.12+; 1.8-1.11 expose it through Player.Spigot instead.
     * Returns null when neither is available.
     */
    public static String getLocale(Player player) {
        if (PLAYER_GET_LOCALE != null) {
            try {
                return (String) PLAYER_GET_LOCALE.invoke(player);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the Spigot accessor
            }
        }

        if (SPIGOT_GET_LOCALE != null) {
            try {
                return (String) SPIGOT_GET_LOCALE.invoke(player.spigot());
            } catch (ReflectiveOperationException | LinkageError e) {
                // No locale available on this server version
            }
        }

        return null;
    }

    private static final Method TELEPORT_ASYNC = resolveTeleportAsync();

    private static Method resolveTeleportAsync() {
        try {
            return Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException | LinkageError e) {
            // Spigot and pre-1.19 Paper: teleportAsync doesn't exist
            return null;
        }
    }

    /**
     * Teleport an entity in a way that is safe under region threading (Folia, Canvas).
     * Those servers throw UnsupportedOperationException from Entity#teleport and require
     * Entity#teleportAsync instead. Falls back to the blocking call on Spigot/Paper, where
     * teleportAsync may not exist.
     */
    public static void teleportSafely(Entity entity, Location location) {
        if (TELEPORT_ASYNC != null) {
            try {
                TELEPORT_ASYNC.invoke(entity, location);
                return;
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the blocking teleport
            }
        }

        entity.teleport(location);
    }

    private static final boolean HAS_GET_PASSENGERS = resolveMethod(Entity.class, "getPassengers") != null;
    private static final Method LEGACY_GET_PASSENGER = resolveMethod(Entity.class, "getPassenger");

    /**
     * Get an entity's passengers (1.11+).
     * Pre-1.11 only supports a single passenger, returned here as a singleton list.
     */
    public static List<Entity> getPassengers(Entity entity) {
        if (HAS_GET_PASSENGERS) {
            return entity.getPassengers();
        }

        if (LEGACY_GET_PASSENGER != null) {
            try {
                Object passenger = LEGACY_GET_PASSENGER.invoke(entity);
                if (passenger instanceof Entity) {
                    return Collections.singletonList((Entity) passenger);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the empty list
            }
        }

        return Collections.emptyList();
    }

    private static final Method SNAPSHOT_BLOCK_TYPE = resolveMethod(
        ChunkSnapshot.class, "getBlockType", int.class, int.class, int.class);
    private static final Method SNAPSHOT_BLOCK_TYPE_ID = resolveMethod(
        ChunkSnapshot.class, "getBlockTypeId", int.class, int.class, int.class);
    private static final Method MATERIAL_BY_ID = resolveMethod(Material.class, "getMaterial", int.class);
    private static final Method SNAPSHOT_BIOME_3D = resolveMethod(
        ChunkSnapshot.class, "getBiome", int.class, int.class, int.class);
    private static final Method SNAPSHOT_BIOME_2D = resolveMethod(
        ChunkSnapshot.class, "getBiome", int.class, int.class);

    /**
     * Get a chunk snapshot's block type (1.13+).
     * Pre-1.13 exposes numeric block ids instead. Returns null if neither is available.
     */
    public static Material getSnapshotBlockType(ChunkSnapshot snapshot, int x, int y, int z) {
        if (SNAPSHOT_BLOCK_TYPE != null) {
            try {
                return (Material) SNAPSHOT_BLOCK_TYPE.invoke(snapshot, x, y, z);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the id-based lookup
            }
        }

        if (SNAPSHOT_BLOCK_TYPE_ID != null && MATERIAL_BY_ID != null) {
            try {
                Object id = SNAPSHOT_BLOCK_TYPE_ID.invoke(snapshot, x, y, z);
                return (Material) MATERIAL_BY_ID.invoke(null, id);
            } catch (ReflectiveOperationException | LinkageError e) {
                // No usable accessor on this version
            }
        }

        return null;
    }

    /**
     * Get a chunk snapshot's biome. Biomes only became three-dimensional in 1.15; older
     * versions take just X and Z. Returns null if neither accessor is available.
     */
    public static Biome getSnapshotBiome(ChunkSnapshot snapshot, int x, int y, int z) {
        if (SNAPSHOT_BIOME_3D != null) {
            try {
                return (Biome) SNAPSHOT_BIOME_3D.invoke(snapshot, x, y, z);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the 2D accessor
            }
        }

        if (SNAPSHOT_BIOME_2D != null) {
            try {
                return (Biome) SNAPSHOT_BIOME_2D.invoke(snapshot, x, z);
            } catch (ReflectiveOperationException | LinkageError e) {
                // No usable accessor on this version
            }
        }

        return null;
    }

    private static final Method BIOME_GET_KEY = resolveMethod(Biome.class, "getKey");

    /**
     * Get a biome's namespaced key (1.13+).
     * Returns null on older versions, where biomes are not keyed.
     */
    public static Object getBiomeKey(Biome biome) {
        if (biome == null || BIOME_GET_KEY == null) return null;

        try {
            return BIOME_GET_KEY.invoke(biome);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * Check whether a BlockFace points along a single axis (1.13+ BlockFace#isCartesian).
     * Computed from the face's mod values so it works on every version.
     */
    public static boolean isCartesian(BlockFace face) {
        return Math.abs(face.getModX()) + Math.abs(face.getModY()) + Math.abs(face.getModZ()) == 1;
    }

    private static final boolean HAS_IGNITING_BLOCK =
        resolveMethod(BlockBurnEvent.class, "getIgnitingBlock") != null;

    /**
     * Get the block that ignited a burning block (1.13+).
     * Returns null on older versions, where the ignition source is unknown.
     */
    public static Block getIgnitingBlock(BlockBurnEvent event) {
        return HAS_IGNITING_BLOCK ? event.getIgnitingBlock() : null;
    }

    private static final boolean HAS_CREATE_EXPLOSION_BREAK_FLAG = resolveMethod(
        World.class, "createExplosion", Location.class, float.class, boolean.class, boolean.class) != null;

    /**
     * Create an explosion, optionally suppressing block damage (1.13+).
     * Pre-1.13 has no breakBlocks flag, so callers that need one must pass zero power.
     */
    public static void createExplosion(
        World world,
        Location location,
        float power,
        boolean setFire,
        boolean breakBlocks
    ) {
        if (HAS_CREATE_EXPLOSION_BREAK_FLAG) {
            world.createExplosion(location, power, setFire, breakBlocks);
            return;
        }

        world.createExplosion(location, power, setFire);
    }

    private static final boolean HAS_HIT_BLOCK = resolveMethod(ProjectileHitEvent.class, "getHitBlock") != null;

    /**
     * Get the block a projectile hit (1.11+).
     * Returns null on older versions, where only the projectile's own location is known.
     */
    public static Block getHitBlock(ProjectileHitEvent event) {
        return HAS_HIT_BLOCK ? event.getHitBlock() : null;
    }

    /**
     * Cancel an event if it is cancellable on this server version.
     * ProjectileHitEvent, for example, only became cancellable in 1.20.2.
     *
     * @return true if the event was cancelled
     */
    public static boolean cancelIfPossible(Object event) {
        if (event instanceof Cancellable) {
            ((Cancellable) event).setCancelled(true);
            return true;
        }
        return false;
    }

    private static final boolean HAS_PORTAL_ENTITY = resolveMethod(PortalCreateEvent.class, "getEntity") != null;
    private static final Method PORTAL_GET_BLOCKS = resolveMethod(PortalCreateEvent.class, "getBlocks");

    /**
     * Get the entity that created a portal (1.14+).
     * Returns null on older versions, where the creator is not reported.
     */
    public static Entity getPortalCreateEntity(PortalCreateEvent event) {
        return HAS_PORTAL_ENTITY ? event.getEntity() : null;
    }

    /**
     * Get the blocks forming a created portal.
     * 1.14+ reports block states; older versions report blocks directly.
     */
    public static List<Block> getPortalCreateBlocks(PortalCreateEvent event) {
        if (PORTAL_GET_BLOCKS == null) return Collections.emptyList();

        Object result;
        try {
            result = PORTAL_GET_BLOCKS.invoke(event);
        } catch (ReflectiveOperationException | LinkageError e) {
            return Collections.emptyList();
        }

        if (!(result instanceof Collection)) return Collections.emptyList();

        List<Block> blocks = new ArrayList<>();
        for (Object element : (Collection<?>) result) {
            if (element instanceof Block) {
                blocks.add((Block) element);
            } else if (element instanceof BlockState) {
                blocks.add(((BlockState) element).getBlock());
            }
        }
        return blocks;
    }

    /**
     * Clear a mob's attack target.
     * Tameable does not extend Creature pre-1.9, so the cast has to be checked.
     */
    public static void clearTarget(Object entity) {
        if (entity instanceof Creature) {
            ((Creature) entity).setTarget(null);
        }
    }

    /**
     * Get a mob's attack target, or null if this entity cannot have one on this version.
     */
    public static LivingEntity getTarget(Object entity) {
        return entity instanceof Creature ? ((Creature) entity).getTarget() : null;
    }

    private static final Method FALLING_BLOCK_DATA = resolveMethod(FallingBlock.class, "getBlockData");
    private static final Method FALLING_BLOCK_MATERIAL = resolveMethod(FallingBlock.class, "getMaterial");
    private static final Method BLOCK_DATA_MATERIAL = resolveBlockDataMaterial();

    private static Method resolveBlockDataMaterial() {
        try {
            return Class.forName("org.bukkit.block.data.BlockData").getMethod("getMaterial");
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            // Pre-1.13: BlockData doesn't exist
            return null;
        }
    }

    /**
     * Get the material a falling block is made of.
     * FallingBlock#getBlockData is 1.13+; older versions expose the material directly.
     */
    public static Material getFallingBlockMaterial(FallingBlock fallingBlock) {
        if (FALLING_BLOCK_DATA != null && BLOCK_DATA_MATERIAL != null) {
            try {
                Object data = FALLING_BLOCK_DATA.invoke(fallingBlock);
                if (data != null) {
                    return (Material) BLOCK_DATA_MATERIAL.invoke(data);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                // Fall through to the legacy accessor
            }
        }

        if (FALLING_BLOCK_MATERIAL != null) {
            try {
                return (Material) FALLING_BLOCK_MATERIAL.invoke(fallingBlock);
            } catch (ReflectiveOperationException | LinkageError e) {
                // No usable accessor on this version
            }
        }

        return null;
    }

    private static final boolean HAS_EGG_ITEM = resolveMethod(Egg.class, "getItem") != null;

    /**
     * Get the item an egg projectile was thrown from (1.15+).
     * Falls back to a plain egg, which is all older versions can throw.
     */
    public static ItemStack getEggItem(Egg egg) {
        if (HAS_EGG_ITEM) {
            ItemStack item = egg.getItem();
            if (item != null) return item;
        }

        return new ItemStack(Material.EGG);
    }

    private static final boolean HAS_INVENTORY_LOCATION = resolveMethod(Inventory.class, "getLocation") != null;

    /**
     * Get an inventory's location (1.9+).
     * Pre-1.9 resolves it through the inventory's holder instead.
     */
    public static Location getInventoryLocation(Inventory inventory) {
        if (HAS_INVENTORY_LOCATION) {
            return inventory.getLocation();
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState) return ((BlockState) holder).getLocation();
        if (holder instanceof Entity) return ((Entity) holder).getLocation();
        return null;
    }

    private static final boolean HAS_COLLISION_SHAPE = resolveMethod(Block.class, "getCollisionShape") != null;

    /**
     * Check whether a block's collision box fails to fill its entire volume (1.13+).
     * Pre-1.13 has no collision shape API, so occlusion is used as an approximation.
     */
    public static boolean hasPartialCollision(Block block) {
        if (HAS_COLLISION_SHAPE) {
            Collection<org.bukkit.util.BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
            return boxes.isEmpty() || !boxes.stream().allMatch(box -> box.getVolume() == 1.0);
        }

        return !block.getType().isOccluding();
    }

    /**
     * Check whether a block or entity state carries a persistent data key (1.14+).
     * Takes and uses only Object so that callers don't reference the persistent data types,
     * which would make their classes fail verification on older servers.
     *
     * @param holder    the potential {@code PersistentDataHolder}
     * @param key       the key to look for
     * @param namespace the namespace the key must belong to, matched loosely
     */
    public static boolean hasPersistentDataKey(Object holder, String key, String namespace) {
        if (holder == null) return false;

        try {
            Class<?> holderClass = Class.forName("org.bukkit.persistence.PersistentDataHolder");
            if (!holderClass.isInstance(holder)) return false;

            Object container = holderClass.getMethod("getPersistentDataContainer").invoke(holder);
            if (container == null) return false;

            Class<?> containerClass = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            Object keys = containerClass.getMethod("getKeys").invoke(container);
            if (!(keys instanceof Collection)) return false;

            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Method getKey = namespacedKeyClass.getMethod("getKey");
            Method getNamespace = namespacedKeyClass.getMethod("getNamespace");

            String wanted = namespace.toLowerCase(java.util.Locale.ROOT);
            for (Object namespacedKey : (Collection<?>) keys) {
                if (!key.equals(getKey.invoke(namespacedKey))) continue;

                String keyNamespace = String.valueOf(getNamespace.invoke(namespacedKey))
                    .toLowerCase(java.util.Locale.ROOT);
                if (keyNamespace.contains(wanted)) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Pre-1.14: no persistent data API
        }

        return false;
    }

    private static final Object BAN_TYPE_PROFILE = resolveBanProfileType();
    private static final Method OFFLINE_PLAYER_PROFILE = resolveMethod(OfflinePlayer.class, "getPlayerProfile");

    private static Object resolveBanProfileType() {
        try {
            return Enum.valueOf(BanList.Type.class, "PROFILE");
        } catch (IllegalArgumentException | LinkageError e) {
            // Pre-1.20.4: profile bans don't exist
            return null;
        }
    }

    /**
     * Ban a player. Profile bans are 1.20.4+; older versions ban by name.
     */
    @SuppressWarnings({ "deprecation", "rawtypes" })
    public static void addBan(Player player, String reason, String source) {
        if (BAN_TYPE_PROFILE != null && OFFLINE_PLAYER_PROFILE != null) {
            try {
                BanList list = Bukkit.getServer().getBanList((BanList.Type) BAN_TYPE_PROFILE);
                Object profile = OFFLINE_PLAYER_PROFILE.invoke(player);
                BanList.class
                    .getMethod("addBan", Object.class, String.class, Date.class, String.class)
                    .invoke(list, profile, reason, null, source);
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // Fall through to the name-based ban list
            }
        }

        BanList list = Bukkit.getServer().getBanList(BanList.Type.NAME);
        list.addBan(player.getName(), reason, (Date) null, source);
    }

    /**
     * Pardon a player. Profile bans are 1.20.4+; older versions pardon by name.
     */
    @SuppressWarnings({ "deprecation", "rawtypes" })
    public static void pardon(OfflinePlayer player) {
        if (BAN_TYPE_PROFILE != null && OFFLINE_PLAYER_PROFILE != null) {
            try {
                BanList list = Bukkit.getServer().getBanList((BanList.Type) BAN_TYPE_PROFILE);
                Object profile = OFFLINE_PLAYER_PROFILE.invoke(player);
                BanList.class.getMethod("pardon", Object.class).invoke(list, profile);
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // Fall through to the name-based ban list
            }
        }

        BanList list = Bukkit.getServer().getBanList(BanList.Type.NAME);
        list.pardon(player.getName());
    }

    /**
     * Build an ItemStack for a material, resolving the item form of block-only materials.
     * Material#isItem is 1.13+ and the BlockType/ItemType APIs are 1.21+.
     */
    public static ItemStack createItemStack(Material material) {
        try {
            if (material.isItem()) {
                return new ItemStack(material);
            }

            org.bukkit.block.BlockType blockType = material.asBlockType();
            if (blockType != null && blockType.hasItemType()) {
                return blockType.getItemType().createItemStack();
            }
            return new ItemStack(Material.DIRT);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Pre-1.13: every material can back an ItemStack directly
            return new ItemStack(material);
        }
    }

    /**
     * Construct a BlockPlaceEvent. The EquipmentSlot parameter is 1.21+; older versions
     * use the hand-less constructor. Returns null if neither constructor is available.
     */
    public static BlockPlaceEvent createBlockPlaceEvent(
        Block placed,
        BlockState replaced,
        Block against,
        ItemStack item,
        Player player,
        boolean canBuild
    ) {
        try {
            return new BlockPlaceEvent(placed, replaced, against, item, player, canBuild, EquipmentSlot.HAND);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Pre-1.21: no EquipmentSlot parameter
        }

        try {
            return BlockPlaceEvent.class
                .getConstructor(
                    Block.class,
                    BlockState.class,
                    Block.class,
                    ItemStack.class,
                    Player.class,
                    boolean.class
                )
                .newInstance(placed, replaced, against, item, player, canBuild);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
