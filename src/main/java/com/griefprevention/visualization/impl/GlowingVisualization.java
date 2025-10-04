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
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class GlowingVisualization extends FakeBlockVisualization {

    private final Map<UUID, Set<BlockDisplay>> playerDisplays = new HashMap<>();
    private final Map<IntVector, BlockData> displayLocations = new HashMap<>();
    // Optional per-position glow color overrides (e.g., ADMIN_CLAIM glowstone corners -> orange)
    private final Map<IntVector, org.bukkit.Color> glowColorOverrides = new HashMap<>();
    private final GriefPrevention plugin;
    private static final float OUTLINE_SCALE = 1.005f;
    private static final float OUTLINE_OFFSET = -(OUTLINE_SCALE - 1.0f) / 2.0f;
    
    public GlowingVisualization(@NotNull World world, @NotNull com.griefprevention.util.IntVector visualizeFrom, int height) {
        super(world, visualizeFrom, height);
        this.plugin = GriefPrevention.instance;
    }

    @Override
    public void handleBlockBreak(@NotNull Player player, @NotNull Block block) {
        // Remove any BlockDisplay(s) for this player at this exact block position
        Set<BlockDisplay> displays = playerDisplays.get(player.getUniqueId());
        if (displays != null && !displays.isEmpty()) {
            int bx = block.getX();
            int by = block.getY();
            int bz = block.getZ();
            // Remove matching display entities (accounting for slight offset)
            displays.removeIf(d -> {
                if (d == null || !d.isValid()) return true; // clean up invalid
                Location dl = d.getLocation();
                // Check if display is at the original position or the offset position
                boolean match = (dl.getBlockX() == bx && dl.getBlockY() == by && dl.getBlockZ() == bz) ||
                               (Math.abs(dl.getX() - (bx + OUTLINE_OFFSET)) < 0.1 &&
                                Math.abs(dl.getY() - by) < 0.1 &&
                                Math.abs(dl.getZ() - (bz + OUTLINE_OFFSET)) < 0.1);
                if (match) {
                    try { d.remove(); } catch (Exception ignored) {}
                }
                return match;
            });
        }

        // Remove from our recorded display locations so future refreshes don't recreate it
        synchronized (this) {
            displayLocations.remove(new IntVector(block.getX(), block.getY(), block.getZ()));
            glowColorOverrides.remove(new IntVector(block.getX(), block.getY(), block.getZ()));
        }

        // so the clientside block (gold/iron/wool/etc.) disappears as well.
        removeElementAt(player, new IntVector(block.getX(), block.getY(), block.getZ()));
    }

    @Override
    protected void apply(@NotNull Player player, @NotNull PlayerData playerData) {
        // Immediately clear ALL existing displays for this player from any previous visualizations
        // This handles cases where multiple visualization calls happen in quick succession
        Set<BlockDisplay> existingDisplays = playerDisplays.get(player.getUniqueId());
        if (existingDisplays != null) {
            existingDisplays.forEach(display -> {
                try {
                    if (display != null && display.isValid()) {
                        display.remove();
                    }
                } catch (Exception e) {
                    // Ignore - entity already removed
                }
            });
            existingDisplays.clear();
        }

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
        if (!player.isOnline()) {
            return;
        }

        // Create a copy of the current display locations (populated by draw())
        Map<IntVector, BlockData> locationsToDisplay;
        synchronized (this) {
            locationsToDisplay = new HashMap<>(displayLocations);
        }

        try {
            // Create new displays set for this player
            Set<BlockDisplay> displays = new HashSet<>();
            playerDisplays.put(player.getUniqueId(), displays);

            // Create displays for each location
            for (Map.Entry<IntVector, BlockData> entry : locationsToDisplay.entrySet()) {
                IntVector pos = entry.getKey();
                if (pos != null && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                    createBlockDisplay(player, pos, entry.getValue(), displays);
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

        // Comprehensive cleanup of all displays associated with this player
        // This handles edge cases where displays might be associated with the player but not properly tracked

        // Clear displays tracked for this specific player
        Set<BlockDisplay> displays = playerDisplays.remove(player.getUniqueId());
        if (displays != null && !displays.isEmpty()) {
            for (BlockDisplay display : displays) {
                try {
                    if (display != null && display.isValid()) {
                        display.remove();
                    }
                } catch (Exception e) {
                    // Ignore - entity already removed
                }
            }
        }

        // Additional safety net: check all display sets for any displays that might belong to this player
        // This is important if multiple GlowingVisualization instances exist
        for (Map.Entry<UUID, Set<BlockDisplay>> entry : playerDisplays.entrySet()) {
            UUID otherPlayerId = entry.getKey();
            if (!otherPlayerId.equals(player.getUniqueId())) {
                Set<BlockDisplay> otherDisplays = entry.getValue();
                if (otherDisplays != null) {
                    // Remove any displays that might have been incorrectly associated with other players
                    // This is a rare edge case but worth checking
                    otherDisplays.removeIf(display -> {
                        try {
                            // If display is invalid or null, remove it from tracking
                            if (display == null || !display.isValid()) {
                                return true;
                            }
                            // Check if this display is actually at a location that should be associated with the current player
                            // This is a heuristic check to catch misassociated displays
                            Location displayLoc = display.getLocation();
                            if (displayLoc != null && displayLoc.getWorld().equals(player.getWorld())) {
                                // If the display is in the same world as the player and close to them,
                                // it might be a leftover from a previous visualization
                                if (displayLoc.distanceSquared(player.getLocation()) < 10000) { // Within 100 blocks
                                    display.remove();
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            // If we can't check the display, assume it's invalid and remove it
                            return true;
                        }
                        return false;
                    });
                }
            }
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
        }
    }
    
    private void createBlockDisplay(Player player, IntVector pos, BlockData blockData, Set<BlockDisplay> displays) {
        if (pos == null || blockData == null || displays == null || !player.isOnline()) {
            return;
        }

        // Don't create new displays if the player logged out
        if (!player.isOnline()) {
            return;
        }
        
        // Don't create duplicate displays (check both original and offset positions)
        if (displays.stream().anyMatch(d ->
            d != null && d.isValid() &&
            ((d.getLocation().getBlockX() == pos.x() && d.getLocation().getBlockY() == pos.y() && d.getLocation().getBlockZ() == pos.z()) ||
             (Math.abs(d.getLocation().getX() - (pos.x() + OUTLINE_OFFSET)) < 0.1 &&
              Math.abs(d.getLocation().getY() - pos.y()) < 0.1 &&
              Math.abs(d.getLocation().getZ() - (pos.z() + OUTLINE_OFFSET)) < 0.1)))) {
            return;
        }

        // For 3D subdivisions and 2D subdivisions, use exact coordinates without terrain snapping
        // This ensures subdivisions show at their exact positions (including on glass)
        boolean isSubdivision = blockData.getMaterial() == Material.WHITE_WOOL ||
                                blockData.getMaterial() == Material.IRON_BLOCK;

        int y;
        if (isSubdivision) {
            // Use exact Y coordinate for subdivisions (both 2D and 3D)
            y = pos.y();
        } else {
            // Use terrain snapping for other visualization types
            Block visibleLocation = getVisibleLocation(pos);
            y = visibleLocation.getY();
        }

        // Create location at the determined position with slight offset to reduce z-fighting
        // Ensure Y is within world bounds to match the terrain calculation bounds
        y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight(), y));
        // Use a more significant offset to ensure proper separation from underlying blocks
        Location loc = new Location(world, pos.x() + 0.01, y + 0.01, pos.z() + 0.01);
        
        // Skip if location is in an unloaded chunk
        if (!loc.getChunk().isLoaded()) {
            return;
        }
        
        // Check for existing displays at this location and remove them
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.1, 0.1, 0.1)) {
            if (entity instanceof BlockDisplay) {
                entity.remove();
            }
        }
        
        // Schedule the display creation using the proper entity scheduler
        SchedulerUtil.runLaterEntity(plugin, player, () -> {
            if (!player.isOnline() || !loc.getChunk().isLoaded()) {
                return;
            }
            
            try {
                // Create and initialize the display entity atomically to avoid global visibility flicker
                BlockDisplay display = world.spawn(loc, BlockDisplay.class, spawned -> {
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

                    // Note: Transformation API may not be available in this Bukkit version
                    // Using position offset instead for z-fighting prevention

                    spawned.setViewRange(96);
                    spawned.setInterpolationDuration(1);

                    // Apply glow color override
                    org.bukkit.Color override;
                    synchronized (GlowingVisualization.this) {
                        override = glowColorOverrides.get(new IntVector(pos.x(), pos.y(), pos.z()));
                    }
                    if (override != null) {
                        spawned.setGlowColorOverride(override);
                    } else if (blockData.getMaterial() == Material.GOLD_BLOCK) {
                        spawned.setGlowColorOverride(org.bukkit.Color.YELLOW);
                    } else if (blockData.getMaterial() == Material.IRON_BLOCK) {
                        spawned.setGlowColorOverride(org.bukkit.Color.WHITE);
                    } else if (blockData.getMaterial() == Material.WHITE_WOOL) {
                        spawned.setGlowColorOverride(org.bukkit.Color.WHITE);
                    } else if (blockData.getMaterial() == Material.DIAMOND_BLOCK) {
                        spawned.setGlowColorOverride(org.bukkit.Color.AQUA);
                    } else if (blockData.getMaterial() == Material.REDSTONE_ORE) {
                        spawned.setGlowColorOverride(org.bukkit.Color.RED);
                    } else if (blockData.getMaterial() == Material.NETHERRACK) {
                        spawned.setGlowColorOverride(org.bukkit.Color.RED);
                    } else if (blockData.getMaterial() == Material.GLOWSTONE) {
                        spawned.setGlowColorOverride(org.bukkit.Color.YELLOW);
                    } else if (blockData.getMaterial() == Material.PUMPKIN) {
                        spawned.setGlowColorOverride(org.bukkit.Color.ORANGE);
                    } else {
                        spawned.setGlowColorOverride(org.bukkit.Color.YELLOW);
                    }
                });

                // Track display for this player
                synchronized (displays) {
                    displays.add(display);
                }

                // Schedule a check to ensure the display is still valid
                SchedulerUtil.runLaterEntity(plugin, player, () -> {
                    if (display.isValid() && !player.getWorld().equals(display.getWorld())) {
                        display.remove();
                    }
                }, 20L);

            } catch (Exception e) {
                plugin.getLogger().warning("Error creating block display at " + loc + ": " + e.getMessage());
                if (e.getCause() != null) {
                    e.getCause().printStackTrace();
                }
            }
        }, 1L);
    }
}