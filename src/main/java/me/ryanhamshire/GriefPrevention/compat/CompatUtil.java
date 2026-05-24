package me.ryanhamshire.GriefPrevention.compat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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

    /**
     * Safely check if a material matches a given name (for newer materials)
     * Returns false if material doesn't exist in this version
     */
    public static boolean isMaterial(Material material, String materialName) {
        try {
            return material == Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Material doesn't exist in this version
            return false;
        }
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
}
