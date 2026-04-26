package com.griefprevention.visualization.impl;

import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import com.griefprevention.util.IntVector;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class GlowingVisualization extends FakeBlockVisualization {

    /** Per-player tracking container (single lock for Folia safety) */
    private static final class Track {
        final Set<BlockDisplay> displays = new HashSet<>();
        final Set<Long> keys = new HashSet<>();
        final Map<BlockDisplay, Long> keyByEntity = new HashMap<>();
        int gen = 0; // increments when track is invalidated (prevents late tasks from resurrecting)
    }
    
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<IntVector, BlockData> displayLocations = new HashMap<>();
    // Optional per-position glow color overrides (e.g., ADMIN_CLAIM glowstone corners -> orange)
    private final Map<IntVector, org.bukkit.Color> glowColorOverrides = new HashMap<>();
    private final GriefPrevention plugin;
    private static final float OUTLINE_SCALE = 1.005f;
    private static final String TAG_BASE = "gp_vis";
    private static final String TAG_SUBDIV = "gp_vis_subdiv";
    private static final String TAG_OUTLINE = "gp_vis_outline";
    private static final AxisAngle4f ROT_IDENTITY = new AxisAngle4f(0f, 0f, 1f, 0f); // angle=0 means identity
    // World min height offset for bit packing (supports y down to -2048)
    private static final int Y_OFFSET = 2048;

    /** Generate per-player tag for display ownership */
    private static String tagFor(Player p) {
        return TAG_BASE + "_" + p.getUniqueId();
    }

    /** Bit-pack block coords into a collision-free long key (x/z ±33M, y 0-4095 after offset) */
    private static long key(int x, int y, int z) {
        int py = y + Y_OFFSET;
        // Guard against out-of-range Y (custom world heights beyond ±2048)
        if ((py & ~0xFFF) != 0) {
            py = Math.max(0, Math.min(0xFFF, py));
        }
        return (((long) x & 0x3FFFFFFL) << 38)
             | (((long) z & 0x3FFFFFFL) << 12)
             | ((long) py & 0xFFFL);
    }

    /** Get default glow color for a block material */
    private static org.bukkit.Color defaultGlowColor(Material m) {
        return switch (m) {
            case GOLD_BLOCK, GLOWSTONE -> org.bukkit.Color.YELLOW;
            case IRON_BLOCK, WHITE_WOOL -> org.bukkit.Color.WHITE;
            case DIAMOND_BLOCK -> org.bukkit.Color.AQUA;
            case REDSTONE_ORE, NETHERRACK -> org.bukkit.Color.RED;
            case PUMPKIN -> org.bukkit.Color.ORANGE;
            case LIME_GLAZED_TERRACOTTA, EMERALD_BLOCK -> org.bukkit.Color.LIME;
            default -> org.bukkit.Color.YELLOW;
        };
    }

    /** Get or create a Track for a player (synchronized on tracks map) */
    private Track getOrCreateTrack(UUID playerId) {
        synchronized (tracks) {
            return tracks.computeIfAbsent(playerId, id -> new Track());
        }
    }

    /** Get existing Track for a player, or null (synchronized on tracks map) */
    private Track getTrack(UUID playerId) {
        synchronized (tracks) {
            return tracks.get(playerId);
        }
    }

    /** Remove and return Track for a player (synchronized on tracks map) */
    private Track removeTrack(UUID playerId) {
        synchronized (tracks) {
            return tracks.remove(playerId);
        }
    }

    /**
     * Remove a display and its associated key from tracking (Track overload for when you already have t).
     * Safe to call with null display. All access synchronized on Track.
     */
    private void untrackDisplay(Track t, BlockDisplay display) {
        if (display == null) return;
        if (t != null) {
            synchronized (t) {
                Long k = t.keyByEntity.remove(display);
                if (k != null) t.keys.remove(k);
                t.displays.remove(display);
            }
        }
        try {
            if (display.isValid()) display.remove();
        } catch (Exception ignored) {}
    }

    /**
     * Remove a display and its associated key from tracking (UUID overload for external callers).
     * Safe to call with null display.
     */
    private void untrackDisplay(UUID playerId, BlockDisplay display) {
        untrackDisplay(getTrack(playerId), display);
    }
    
    public GlowingVisualization(@NotNull World world, @NotNull com.griefprevention.util.IntVector visualizeFrom, int height) {
        super(world, visualizeFrom, height);
        this.plugin = GriefPrevention.instance;
    }

    @Override
    public void handleBlockBreak(@NotNull Player player, @NotNull Block block) {
        UUID playerId = player.getUniqueId();
        Track t = getTrack(playerId);
        
        if (t != null) {
            int bx = block.getX();
            int by = block.getY();
            int bz = block.getZ();
            
            // Collect targets under lock, then untrack outside
            Set<BlockDisplay> toRemove = new HashSet<>();
            synchronized (t) {
                for (BlockDisplay d : t.displays) {
                    if (d == null || !d.isValid()) {
                        toRemove.add(d);
                        continue;
                    }
                    Location dl = d.getLocation();
                    if (dl.getWorld().equals(block.getWorld())
                            && dl.getBlockX() == bx
                            && dl.getBlockY() == by
                            && dl.getBlockZ() == bz) {
                        toRemove.add(d);
                    }
                }
            }
            // Untrack outside the iteration (use Track overload since we already have t)
            for (BlockDisplay d : toRemove) {
                untrackDisplay(t, d);
            }
        }

        // Remove from our recorded display locations (two-pass: collect removed, then clean overrides)
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        synchronized (this) {
            Set<IntVector> removed = new HashSet<>();
            displayLocations.entrySet().removeIf(e -> {
                IntVector v = e.getKey();
                if (v.x() != bx || v.z() != bz) return false;
                Material m = e.getValue().getMaterial();
                boolean isSubdiv = (m == Material.WHITE_WOOL || m == Material.IRON_BLOCK);
                boolean shouldRemove = isSubdiv ? (v.y() == by) : true;
                if (shouldRemove) removed.add(v);
                return shouldRemove;
            });
            glowColorOverrides.keySet().removeIf(removed::contains);
        }

        // so the clientside block (gold/iron/wool/etc.) disappears as well.
        removeElementAt(player, new IntVector(bx, by, bz));
    }

    @Override
    protected void apply(@NotNull Player player, @NotNull PlayerData playerData) {
        // Nuke and rebuild: remove existing entities, clear track, then rebuild
        UUID playerId = player.getUniqueId();
        Track t = getTrack(playerId);
        
        if (t != null) {
            // Copy under lock, bump generation, then remove entities outside lock
            Set<BlockDisplay> toRemove;
            synchronized (t) {
                t.gen++; // invalidate any pending tasks
                toRemove = new HashSet<>(t.displays);
                t.displays.clear();
                t.keys.clear();
                t.keyByEntity.clear();
            }
            for (BlockDisplay d : toRemove) {
                try {
                    if (d != null && d.isValid()) d.remove();
                } catch (Exception ignored) {}
            }
        }
        // Remove the track entry entirely (will be recreated fresh)
        removeTrack(playerId);

        synchronized (this) {
            displayLocations.clear();
            glowColorOverrides.clear();
        }

        // Call super.apply() to show the underlying FakeBlockVisualization (yellow outline blocks)
        super.apply(player, playerData);

        // Immediately create displays for better responsiveness and to prevent duplicates
        createDisplaysForPlayer(player);
    }

    /**
     * Immediately create displays for a player to avoid timing issues with multiple visualization calls
     */
    private void createDisplaysForPlayer(@NotNull Player player) {
        if (!player.isOnline()) return;

        // Create a copy of the current display locations (populated by draw())
        Map<IntVector, BlockData> locationsToDisplay;
        synchronized (this) {
            locationsToDisplay = new HashMap<>(displayLocations);
        }

        UUID playerId = player.getUniqueId();
        Track t = getOrCreateTrack(playerId);

        try {
            // Create displays for each location
            for (Map.Entry<IntVector, BlockData> entry : locationsToDisplay.entrySet()) {
                IntVector pos = entry.getKey();
                if (pos != null && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                    createBlockDisplay(player, pos, entry.getValue(), t);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating block displays: " +
                (e.getMessage() != null ? e.getMessage() : "Unknown error (message was null)"));
            if (e.getCause() != null) {
                plugin.getLogger().warning("Cause: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            } else {
                plugin.getLogger().warning("No cause available");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void revert(@NotNull Player player) {
        super.revert(player);

        UUID playerId = player.getUniqueId();
        Track t = removeTrack(playerId);
        boolean hadTrackedDisplays = false;
        
        if (t != null) {
            // Copy under lock, bump generation, then remove entities outside
            Set<BlockDisplay> toRemove;
            synchronized (t) {
                t.gen++; // invalidate any pending tasks
                hadTrackedDisplays = !t.displays.isEmpty();
                toRemove = new HashSet<>(t.displays);
                t.displays.clear();
                t.keys.clear();
                t.keyByEntity.clear();
            }
            for (BlockDisplay d : toRemove) {
                if (d == null) continue;
                try {
                    SchedulerUtil.runLaterEntity(plugin, d, () -> {
                        try {
                            if (d.isValid()) d.remove();
                        } catch (Exception ignored) {}
                    }, 0L);
                } catch (Exception ignored) {}
            }
        }

        // Safety net: only scan if we suspect orphans (no tracked displays but player had visualization)
        if (!hadTrackedDisplays) {
            final String playerTag = tagFor(player);
            final World scanWorld = player.getWorld();
            final Location scanLocation = player.getLocation();
            try {
                SchedulerUtil.runAtLocation(plugin, scanLocation, () -> {
                    try {
                        for (Entity entity : scanWorld.getNearbyEntities(scanLocation, 32, 32, 32)) {
                            if (entity instanceof BlockDisplay && entity.getScoreboardTags().contains(playerTag)) {
                                try {
                                    SchedulerUtil.runLaterEntity(plugin, entity, () -> {
                                        try {
                                            if (entity.isValid()) entity.remove();
                                        } catch (Exception ignored) {}
                                    }, 0L);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }

        // Clear display locations to ensure no stale data remains
        synchronized (this) {
            displayLocations.clear();
            glowColorOverrides.clear();
        }
    }

    @Override
    protected void draw(@NotNull Player player, @NotNull Boundary boundary) {
        // Call super.draw() for all types to show underlying visualization (white/yellow outline blocks)
        super.draw(player, boundary);
    }
    
    /**
     * Helper method to create fake block elements and track them for glow display creation
     */
    @Override
    protected void onElementAdded(@NotNull IntVector location, @NotNull BlockData fakeData, @NotNull VisualizationType type) {
        synchronized (this) {
            // Track ALL fake block locations for glow display creation, not just the ones we create manually
            displayLocations.put(location, fakeData);

            // Apply glow color overrides for specific types
            if (type == VisualizationType.ADMIN_CLAIM && fakeData.getMaterial() == Material.GLOWSTONE) {
                glowColorOverrides.put(location, org.bukkit.Color.ORANGE);
            }
            // Red glow for conflict zones
            if (type == VisualizationType.CONFLICT_ZONE || type == VisualizationType.CONFLICT_ZONE_3D) {
                glowColorOverrides.put(location, org.bukkit.Color.RED);
            }
        }
    }
    
    private void createBlockDisplay(Player player, IntVector pos, BlockData blockData, Track t) {
        if (pos == null || blockData == null || t == null || !player.isOnline()) return;
        
        // For 3D subdivisions and 2D subdivisions, use exact coordinates without terrain snapping
        Material mat = blockData.getMaterial();
        boolean isExactPlacement = mat == Material.WHITE_WOOL || mat == Material.IRON_BLOCK ||
                                   mat == Material.REDSTONE_ORE || mat == Material.NETHERRACK;

        int y;
        if (isExactPlacement) {
            y = pos.y();
        } else {
            Block visibleLocation = getVisibleLocation(pos);
            y = visibleLocation.getY();
        }

        // Ensure Y is within world bounds
        y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight(), y));
        
        // O(1) duplicate check - reserve key immediately under Track lock, capture generation
        long posKey = key(pos.x(), y, pos.z());
        final int myGen;
        synchronized (t) {
            myGen = t.gen;
            if (!t.keys.add(posKey)) {
                return; // already reserved/exists
            }
        }

        // Spawn at exact block corner - alignment is handled by Transformation
        Location loc = new Location(world, pos.x(), y, pos.z());
        
        // Skip if location is in an unloaded chunk - release reserved key
        if (!loc.getChunk().isLoaded()) {
            synchronized (t) {
                t.keys.remove(posKey);
            }
            return;
        }
        
        // Tags for this display
        String playerTag = tagFor(player);
        String typeTag = isExactPlacement ? TAG_SUBDIV : TAG_OUTLINE;
        UUID playerId = player.getUniqueId();
        
        // Schedule the display creation using the proper entity scheduler
        SchedulerUtil.runLaterEntity(plugin, player, () -> {
            // Check if track was invalidated (apply/revert called) before we got here
            synchronized (t) {
                if (t.gen != myGen) {
                    t.keys.remove(posKey);
                    return;
                }
            }
            
            if (!player.isOnline() || !loc.getChunk().isLoaded()) {
                // Release reserved key on early exit
                synchronized (t) {
                    if (t.gen == myGen) t.keys.remove(posKey);
                }
                return;
            }
            
            try {
                // Create and initialize the display entity atomically to avoid global visibility flicker
                BlockDisplay display = world.spawn(loc, BlockDisplay.class, spawned -> {
                    // Tag this display for identification
                    spawned.addScoreboardTag(TAG_BASE);      // base tag for all GP displays
                    spawned.addScoreboardTag(playerTag);     // per-player tag
                    spawned.addScoreboardTag(typeTag);       // type tag (subdiv vs outline)
                    
                    // Make per-player only
                    spawned.setVisibleByDefault(false);
                    // Paper/Folia-compatible per-player visibility
                    // Note: showEntity method may not be available in all Bukkit versions
                    try {
                        player.showEntity(plugin, spawned);
                    } catch (Exception e) {
                        // Fallback for older Bukkit versions
                    }

                    spawned.setBlock(blockData);
                    spawned.setGlowing(true);
                    spawned.setBrightness(new Display.Brightness(12, 12));
                    spawned.setShadowStrength(0.0f);
                    spawned.setShadowRadius(0.0f);

                    // Apply transformation: scale slightly larger and offset to stay centered
                    // offset = -(scale - 1) / 2 recenters the scaled block on the original corner
                    float s = OUTLINE_SCALE;
                    float o = -(s - 1.0f) / 2.0f;
                    spawned.setTransformation(new Transformation(
                            new Vector3f(o, o, o),  // translation (recenter for scale)
                            ROT_IDENTITY,           // left rotation (identity)
                            new Vector3f(s, s, s),  // scale
                            ROT_IDENTITY            // right rotation (identity)
                    ));

                    spawned.setViewRange(96);
                    spawned.setInterpolationDuration(0);  // No lerp slide on spawn

                    // Apply glow color override (use pos directly as key - IntVector is immutable)
                    org.bukkit.Color override;
                    synchronized (GlowingVisualization.this) {
                        override = glowColorOverrides.get(pos);
                    }
                    spawned.setGlowColorOverride(override != null ? override : defaultGlowColor(blockData.getMaterial()));
                });

                // Sanity check: if spawn succeeded but entity is instantly invalid, release key
                if (display == null || !display.isValid()) {
                    synchronized (t) {
                        if (t.gen == myGen) t.keys.remove(posKey);
                    }
                    return;
                }

                // Track display and its key for this player (if track still valid)
                synchronized (t) {
                    if (t.gen != myGen) {
                        // Track was invalidated after spawn - remove the orphan entity
                        display.remove();
                        return;
                    }
                    t.displays.add(display);
                    t.keyByEntity.put(display, posKey);
                }

                // Schedule a check to ensure the display is still valid
                SchedulerUtil.runLaterEntity(plugin, player, () -> {
                    if (display.isValid() && !player.getWorld().equals(display.getWorld())) {
                        untrackDisplay(playerId, display);
                    }
                }, 20L);

            } catch (Exception e) {
                // Release reserved key on failure (if track still valid)
                synchronized (t) {
                    if (t.gen == myGen) t.keys.remove(posKey);
                }
                plugin.getLogger().warning("Error creating block display at " + loc + ": " + e.getMessage());
                if (e.getCause() != null) {
                    e.getCause().printStackTrace();
                }
            }
        }, 1L);
    }
}