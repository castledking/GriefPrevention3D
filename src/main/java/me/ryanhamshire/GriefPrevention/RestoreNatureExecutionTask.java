/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Folia-safe main thread task that applies the processed restore nature changes to the world.
 * Only modifies unclaimed blocks, unless in aggressive mode where it modifies all blocks.
 */
public class RestoreNatureExecutionTask implements Runnable {

    private final BlockSnapshot[][][] snapshots;
    @SuppressWarnings("unused")
    private final int miny;
    private final Location lesserCorner;
    private final Location greaterCorner;
    private final UUID playerID;
    private final boolean aggressiveMode;

    public RestoreNatureExecutionTask(BlockSnapshot[][][] snapshots, int miny,
            Location lesserCorner, Location greaterCorner, UUID playerID, boolean aggressiveMode) {
        this.snapshots = snapshots;
        this.miny = miny;
        this.lesserCorner = lesserCorner;
        this.greaterCorner = greaterCorner;
        this.playerID = playerID;
        this.aggressiveMode = aggressiveMode;
    }

    @Override
    public void run() {
        // Apply changes to the world
        // In aggressive mode, modifies all blocks (including claimed ones)
        // Otherwise, only modifies unclaimed blocks
        // Note: the edge of the results is not applied (1-block-wide band around the outside)
        // Those data were sent to the processing thread for reference purposes only
        Claim cachedClaim = null;

        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                for (int y = 0; y < snapshots[0].length; y++) {
                    BlockSnapshot blockUpdate = snapshots[x][y][z];
                    if (blockUpdate == null) continue;

                    Block currentBlock = blockUpdate.location.getBlock();

                    // Check if the block actually changed
                    if (blockUpdate.material != currentBlock.getType() ||
                            !blockUpdate.blockData.equals(currentBlock.getBlockData())) {

                        // In aggressive mode, modify all blocks (including claimed ones)
                        // Otherwise, only modify unclaimed blocks
                        if (!aggressiveMode) {
                            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(
                                    blockUpdate.location, false, cachedClaim);
                            if (claim != null) {
                                cachedClaim = claim;
                                continue; // Skip claimed blocks
                            }
                        }

                        try {
                            setBlockData(currentBlock, blockUpdate.blockData);
                        } catch (IllegalArgumentException e) {
                            // Just skip this block if there's an issue
                        }
                    }
                }
            }
        }

        // Clean up entities in the affected area
        cleanupEntities();

        // Show visualization to the player who started the restoration
        showVisualization();
    }

    private void cleanupEntities() {
        if (lesserCorner == null || lesserCorner.getWorld() == null) return;

        Chunk chunk = lesserCorner.getChunk();
        Entity[] entities = chunk.getEntities();

        for (Entity entity : entities) {
            if (entity instanceof Player || entity instanceof Animals) {
                // For players and animals, ensure there's air where they're standing
                Block feetBlock = entity.getLocation().getBlock();
                
                // Only clear if the entity is in the restoration area
                Location entityLoc = entity.getLocation();
                if (isInRestorationArea(entityLoc)) {
                    // Check if block is not already air and not claimed (unless in aggressive mode)
                    if (feetBlock.getType() != Material.AIR) {
                        if (!aggressiveMode) {
                            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(
                                    feetBlock.getLocation(), false, null);
                            if (claim == null) {
                                feetBlock.setType(Material.AIR);
                                feetBlock.getRelative(BlockFace.UP).setType(Material.AIR);
                            }
                        } else {
                            // In aggressive mode, clear the block regardless of claims
                            feetBlock.setType(Material.AIR);
                            feetBlock.getRelative(BlockFace.UP).setType(Material.AIR);
                        }
                    }
                }
            } else {
                // For other entities, remove them if not protected
                if (entity instanceof Hanging) {
                    // Hanging entities (paintings, item frames) are protected in claims unless in aggressive mode
                    if (aggressiveMode || GriefPrevention.instance.dataStore.getClaimAt(
                            entity.getLocation(), false, null) == null) {
                        entity.remove();
                    }
                } else {
                    // Remove other non-player, non-animal entities in restoration area
                    if (isInRestorationArea(entity.getLocation())) {
                        entity.remove();
                    }
                }
            }
        }
    }

    private boolean isInRestorationArea(Location loc) {
        if (lesserCorner == null || greaterCorner == null) return false;
        if (!loc.getWorld().equals(lesserCorner.getWorld())) return false;

        return loc.getBlockX() >= lesserCorner.getBlockX() &&
                loc.getBlockX() <= greaterCorner.getBlockX() &&
                loc.getBlockZ() >= lesserCorner.getBlockZ() &&
                loc.getBlockZ() <= greaterCorner.getBlockZ();
    }

    private void showVisualization() {
        if (playerID == null) return;

        Player player = Bukkit.getPlayer(playerID);
        if (player == null || !player.isOnline()) return;

        // Create a temporary claim object for visualization purposes
        Claim visualClaim = new Claim(
                lesserCorner,
                greaterCorner,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null
        );

        BoundaryVisualization.visualizeClaim(player, visualClaim, VisualizationType.RESTORE_NATURE, lesserCorner.getBlock());

        GriefPrevention.sendMessage(player, TextMode.Success, Messages.RestoreNatureActivate);

        // Auto-revert visualization after 5 seconds (100 ticks)
        SchedulerUtil.runLaterEntity(GriefPrevention.instance, player, () -> {
            if (player.isOnline()) {
                PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
                playerData.setVisibleBoundaries(null);
            }
        }, 100L);
    }

    private static void setBlockData(Block block, Object blockData) {
        if (blockData == null) {
            return;
        }
        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData", false, RestoreNatureExecutionTask.class.getClassLoader());
            Block.class.getMethod("setBlockData", blockDataClass).invoke(block, blockData);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Legacy servers don't support BlockData, just set material
            block.setType(block.getType(), false);
        }
    }
}
