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

import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Folia-safe async task that processes world data to restore nature.
 * After processing, schedules RestoreNatureExecutionTask on the main thread/region.
 */
public class RestoreNatureProcessingTask implements Runnable {

    // World information captured from the main thread
    private final BlockSnapshot[][][] snapshots;
    private final int miny;
    private final Environment environment;
    private final Location lesserBoundaryCorner;
    private final Location greaterBoundaryCorner;
    private final UUID playerID;
    private final Biome biome;
    private final boolean creativeMode;
    private final int seaLevel;
    private final boolean aggressiveMode;

    // Materials that shouldn't hang in the air naturally
    private final Set<Material> notAllowedToHang;
    // Player-placed blocks that should be removed
    private final Set<Material> playerBlocks;

    public RestoreNatureProcessingTask(BlockSnapshot[][][] snapshots, int miny, Environment environment,
            Biome biome, Location lesserBoundaryCorner, Location greaterBoundaryCorner,
            int seaLevel, boolean aggressiveMode, boolean creativeMode, Player player) {
        this.snapshots = snapshots;
        this.miny = Math.max(0, miny);
        this.environment = environment;
        this.lesserBoundaryCorner = lesserBoundaryCorner;
        this.greaterBoundaryCorner = greaterBoundaryCorner;
        this.biome = biome;
        this.seaLevel = seaLevel;
        this.aggressiveMode = aggressiveMode;
        this.playerID = player != null ? player.getUniqueId() : null;
        this.creativeMode = creativeMode;

        // Initialize materials that shouldn't hang
        this.notAllowedToHang = EnumSet.of(
                Material.DIRT,
                Material.SHORT_GRASS,
                Material.TALL_GRASS,
                Material.SNOW,
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG
        );

        if (this.aggressiveMode) {
            this.notAllowedToHang.add(Material.GRASS_BLOCK);
            this.notAllowedToHang.add(Material.STONE);
        }

        // Initialize player-placed blocks
        this.playerBlocks = EnumSet.noneOf(Material.class);
        this.playerBlocks.addAll(getPlayerBlocks(this.environment, this.biome));

        // In aggressive or creative mode, also treat these blocks as player-placed
        if (this.aggressiveMode || this.creativeMode) {
            this.playerBlocks.add(Material.IRON_ORE);
            this.playerBlocks.add(Material.DEEPSLATE_IRON_ORE);
            this.playerBlocks.add(Material.GOLD_ORE);
            this.playerBlocks.add(Material.DEEPSLATE_GOLD_ORE);
            this.playerBlocks.add(Material.DIAMOND_ORE);
            this.playerBlocks.add(Material.DEEPSLATE_DIAMOND_ORE);
            this.playerBlocks.add(Material.MELON);
            this.playerBlocks.add(Material.MELON_STEM);
            this.playerBlocks.add(Material.BEDROCK);
            this.playerBlocks.add(Material.COAL_ORE);
            this.playerBlocks.add(Material.DEEPSLATE_COAL_ORE);
            this.playerBlocks.add(Material.PUMPKIN);
            this.playerBlocks.add(Material.PUMPKIN_STEM);
        }

        if (this.aggressiveMode) {
            this.playerBlocks.add(Material.OAK_LEAVES);
            this.playerBlocks.add(Material.SPRUCE_LEAVES);
            this.playerBlocks.add(Material.BIRCH_LEAVES);
            this.playerBlocks.add(Material.JUNGLE_LEAVES);
            this.playerBlocks.add(Material.ACACIA_LEAVES);
            this.playerBlocks.add(Material.DARK_OAK_LEAVES);
            this.playerBlocks.add(Material.MANGROVE_LEAVES);
            this.playerBlocks.add(Material.CHERRY_LEAVES);
            this.playerBlocks.add(Material.OAK_LOG);
            this.playerBlocks.add(Material.SPRUCE_LOG);
            this.playerBlocks.add(Material.BIRCH_LOG);
            this.playerBlocks.add(Material.JUNGLE_LOG);
            this.playerBlocks.add(Material.ACACIA_LOG);
            this.playerBlocks.add(Material.DARK_OAK_LOG);
            this.playerBlocks.add(Material.MANGROVE_LOG);
            this.playerBlocks.add(Material.CHERRY_LOG);
        }
    }

    @Override
    public void run() {
        // Remove floating blocks (blocks that shouldn't be suspended in air)
        removeHanging();

        // Remove player-placed blocks
        removePlayerBlocks();

        // Remove walls/floors that separate natural areas
        removeWalls();

        // Fill in empty spaces with appropriate materials
        fillHoles();

        // Cover surface with grass/sand as appropriate
        coverSurface();

        // Remove floating blocks again after other changes
        removeHanging();

        // Schedule the execution task at the location's region (Folia-safe)
        SchedulerUtil.runAtLocation(GriefPrevention.instance, lesserBoundaryCorner, () -> {
            new RestoreNatureExecutionTask(snapshots, miny, lesserBoundaryCorner, greaterBoundaryCorner, playerID, aggressiveMode).run();
        });
    }

    private void removeHanging() {
        // Iterate from bottom to top, removing blocks that shouldn't float
        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                for (int y = miny + 1; y < snapshots[0].length; y++) {
                    BlockSnapshot block = snapshots[x][y][z];
                    BlockSnapshot blockBelow = snapshots[x][y - 1][z];

                    if (block != null && blockBelow != null) {
                        if (notAllowedToHang.contains(block.material) && blockBelow.material == Material.AIR) {
                            block.material = Material.AIR;
                            block.blockData = Material.AIR.createBlockData();
                        }
                    }
                }
            }
        }
    }

    private void removePlayerBlocks() {
        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                for (int y = miny; y < snapshots[0].length; y++) {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (block != null && playerBlocks.contains(block.material) && !isProtectedFromRestore(x, y, z)) {
                        block.material = Material.AIR;
                        block.blockData = Material.AIR.createBlockData();
                    }
                }
            }
        }
    }

    private void removeWalls() {
        // Remove thin walls of stone/dirt that players may have used to grief
        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                for (int y = miny; y < snapshots[0].length - 1; y++) {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (block == null) continue;
                    if (isProtectedFromRestore(x, y, z)) continue;

                    // Check for thin walls (solid block with air on opposite sides)
                    if (block.material.isSolid()) {
                        // Check X-axis walls
                        if (x > 0 && x < snapshots.length - 1) {
                            BlockSnapshot left = snapshots[x - 1][y][z];
                            BlockSnapshot right = snapshots[x + 1][y][z];
                            if (left != null && right != null &&
                                    left.material == Material.AIR && right.material == Material.AIR) {
                                block.material = Material.AIR;
                                block.blockData = Material.AIR.createBlockData();
                                continue;
                            }
                        }

                        // Check Z-axis walls
                        if (z > 0 && z < snapshots[0][0].length - 1) {
                            BlockSnapshot front = snapshots[x][y][z - 1];
                            BlockSnapshot back = snapshots[x][y][z + 1];
                            if (front != null && back != null &&
                                    front.material == Material.AIR && back.material == Material.AIR) {
                                block.material = Material.AIR;
                                block.blockData = Material.AIR.createBlockData();
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isProtectedFromRestore(int x, int y, int z) {
        BlockSnapshot block = snapshots[x][y][z];
        if (block != null && block.protectedFromRestore) {
            return true;
        }

        for (BlockFace face : BlockFace.values()) {
            if (!face.isCartesian()) {
                continue;
            }

            BlockSnapshot adjacent = getRelativeSnapshot(x, y, z, face);
            if (adjacent == null || !adjacent.protectedFromRestore) {
                continue;
            }

            if (adjacent.blockData instanceof WallSign wallSign) {
                if (wallSign.getFacing() == face) {
                    return true;
                }
            } else if (face == BlockFace.UP) {
                return true;
            }
        }

        return false;
    }

    private BlockSnapshot getRelativeSnapshot(int x, int y, int z, BlockFace face) {
        int relativeX = x + face.getModX();
        int relativeY = y + face.getModY();
        int relativeZ = z + face.getModZ();

        if (relativeX < 0 || relativeX >= snapshots.length
                || relativeY < 0 || relativeY >= snapshots[0].length
                || relativeZ < 0 || relativeZ >= snapshots[0][0].length) {
            return null;
        }

        return snapshots[relativeX][relativeY][relativeZ];
    }

    private void fillHoles() {
        Material fillMaterial = getFillMaterial();

        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                // Find surface level (highest non-air block)
                int surfaceY = -1;
                for (int y = snapshots[0].length - 1; y >= miny; y--) {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (block != null && block.material != Material.AIR &&
                            block.material != Material.WATER && !block.material.name().contains("LEAVES")) {
                        surfaceY = y;
                        break;
                    }
                }

                if (surfaceY < miny) continue;

                // Fill holes below surface
                for (int y = miny; y < surfaceY; y++) {
                    BlockSnapshot block = snapshots[x][y][z];
                    if (block != null && block.material == Material.AIR) {
                        // Check if surrounded by solid blocks (it's a hole)
                        int solidNeighbors = 0;
                        if (x > 0 && snapshots[x - 1][y][z] != null && snapshots[x - 1][y][z].material.isSolid())
                            solidNeighbors++;
                        if (x < snapshots.length - 1 && snapshots[x + 1][y][z] != null && snapshots[x + 1][y][z].material.isSolid())
                            solidNeighbors++;
                        if (z > 0 && snapshots[x][y][z - 1] != null && snapshots[x][y][z - 1].material.isSolid())
                            solidNeighbors++;
                        if (z < snapshots[0][0].length - 1 && snapshots[x][y][z + 1] != null && snapshots[x][y][z + 1].material.isSolid())
                            solidNeighbors++;

                        if (solidNeighbors >= 3) {
                            block.material = fillMaterial;
                            block.blockData = fillMaterial.createBlockData();
                        }
                    }
                }
            }
        }
    }

    private void coverSurface() {
        Material surfaceMaterial = getSurfaceMaterial();
        Material underMaterial = getUnderSurfaceMaterial();

        for (int x = 1; x < snapshots.length - 1; x++) {
            for (int z = 1; z < snapshots[0][0].length - 1; z++) {
                // Find surface level
                for (int y = snapshots[0].length - 2; y >= miny; y--) {
                    BlockSnapshot block = snapshots[x][y][z];
                    BlockSnapshot blockAbove = snapshots[x][y + 1][z];

                    if (block != null && blockAbove != null) {
                        // If this is a solid block with air above, it's the surface
                        if (block.material.isSolid() && blockAbove.material == Material.AIR) {
                            // Replace with appropriate surface material
                            if (block.material == Material.DIRT || block.material == Material.STONE ||
                                    block.material == Material.COBBLESTONE || block.material == Material.GRAVEL) {
                                block.material = surfaceMaterial;
                                block.blockData = surfaceMaterial.createBlockData();

                                // Also fix the block below if needed
                                if (y > miny) {
                                    BlockSnapshot blockBelow = snapshots[x][y - 1][z];
                                    if (blockBelow != null && blockBelow.material == Material.STONE) {
                                        blockBelow.material = underMaterial;
                                        blockBelow.blockData = underMaterial.createBlockData();
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private Material getFillMaterial() {
        if (environment == Environment.NETHER) {
            return Material.NETHERRACK;
        } else if (environment == Environment.THE_END) {
            return Material.END_STONE;
        }
        return Material.DIRT;
    }

    private Material getSurfaceMaterial() {
        if (environment == Environment.NETHER) {
            return Material.NETHERRACK;
        } else if (environment == Environment.THE_END) {
            return Material.END_STONE;
        }

        // Check biome for appropriate surface
        String biomeName = biome != null ? biome.name() : "";
        if (biomeName.contains("DESERT") || biomeName.contains("BEACH")) {
            return Material.SAND;
        } else if (biomeName.contains("BADLANDS") || biomeName.contains("MESA")) {
            return Material.RED_SAND;
        } else if (biomeName.contains("MUSHROOM")) {
            return Material.MYCELIUM;
        }

        return Material.GRASS_BLOCK;
    }

    private Material getUnderSurfaceMaterial() {
        if (environment == Environment.NETHER) {
            return Material.NETHERRACK;
        } else if (environment == Environment.THE_END) {
            return Material.END_STONE;
        }

        String biomeName = biome != null ? biome.name() : "";
        if (biomeName.contains("DESERT") || biomeName.contains("BEACH")) {
            return Material.SAND;
        } else if (biomeName.contains("BADLANDS") || biomeName.contains("MESA")) {
            return Material.RED_SAND;
        }

        return Material.DIRT;
    }

    /**
     * Gets the set of materials considered to be player-placed based on environment and biome
     */
    public static Set<Material> getPlayerBlocks(Environment environment, Biome biome) {
        Set<Material> playerBlocks = EnumSet.noneOf(Material.class);

        // Common player blocks across all environments
        playerBlocks.add(Material.FIRE);
        playerBlocks.add(Material.BEDROCK);
        playerBlocks.add(Material.COBBLESTONE);
        playerBlocks.add(Material.TORCH);
        playerBlocks.add(Material.WALL_TORCH);
        playerBlocks.add(Material.LADDER);
        playerBlocks.add(Material.CRAFTING_TABLE);
        playerBlocks.add(Material.FURNACE);
        playerBlocks.add(Material.CHEST);
        playerBlocks.add(Material.TRAPPED_CHEST);
        playerBlocks.add(Material.OAK_SIGN);
        playerBlocks.add(Material.SPRUCE_SIGN);
        playerBlocks.add(Material.BIRCH_SIGN);
        playerBlocks.add(Material.JUNGLE_SIGN);
        playerBlocks.add(Material.ACACIA_SIGN);
        playerBlocks.add(Material.DARK_OAK_SIGN);
        playerBlocks.add(Material.OAK_WALL_SIGN);
        playerBlocks.add(Material.SPRUCE_WALL_SIGN);
        playerBlocks.add(Material.BIRCH_WALL_SIGN);
        playerBlocks.add(Material.JUNGLE_WALL_SIGN);
        playerBlocks.add(Material.ACACIA_WALL_SIGN);
        playerBlocks.add(Material.DARK_OAK_WALL_SIGN);
        playerBlocks.add(Material.OAK_FENCE);
        playerBlocks.add(Material.NETHER_BRICK_FENCE);
        playerBlocks.add(Material.GLASS);
        playerBlocks.add(Material.GLASS_PANE);
        playerBlocks.add(Material.OAK_DOOR);
        playerBlocks.add(Material.IRON_DOOR);
        playerBlocks.add(Material.RAIL);
        playerBlocks.add(Material.POWERED_RAIL);
        playerBlocks.add(Material.DETECTOR_RAIL);
        playerBlocks.add(Material.ACTIVATOR_RAIL);
        playerBlocks.add(Material.TNT);
        playerBlocks.add(Material.BOOKSHELF);
        playerBlocks.add(Material.JACK_O_LANTERN);
        playerBlocks.add(Material.STONE_BRICKS);
        playerBlocks.add(Material.MOSSY_STONE_BRICKS);
        playerBlocks.add(Material.CRACKED_STONE_BRICKS);
        playerBlocks.add(Material.CHISELED_STONE_BRICKS);

        // All wool colors
        playerBlocks.add(Material.WHITE_WOOL);
        playerBlocks.add(Material.ORANGE_WOOL);
        playerBlocks.add(Material.MAGENTA_WOOL);
        playerBlocks.add(Material.LIGHT_BLUE_WOOL);
        playerBlocks.add(Material.YELLOW_WOOL);
        playerBlocks.add(Material.LIME_WOOL);
        playerBlocks.add(Material.PINK_WOOL);
        playerBlocks.add(Material.GRAY_WOOL);
        playerBlocks.add(Material.LIGHT_GRAY_WOOL);
        playerBlocks.add(Material.CYAN_WOOL);
        playerBlocks.add(Material.PURPLE_WOOL);
        playerBlocks.add(Material.BLUE_WOOL);
        playerBlocks.add(Material.BROWN_WOOL);
        playerBlocks.add(Material.GREEN_WOOL);
        playerBlocks.add(Material.RED_WOOL);
        playerBlocks.add(Material.BLACK_WOOL);

        // Planks and slabs
        playerBlocks.add(Material.OAK_PLANKS);
        playerBlocks.add(Material.SPRUCE_PLANKS);
        playerBlocks.add(Material.BIRCH_PLANKS);
        playerBlocks.add(Material.JUNGLE_PLANKS);
        playerBlocks.add(Material.ACACIA_PLANKS);
        playerBlocks.add(Material.DARK_OAK_PLANKS);
        playerBlocks.add(Material.OAK_SLAB);
        playerBlocks.add(Material.SPRUCE_SLAB);
        playerBlocks.add(Material.BIRCH_SLAB);
        playerBlocks.add(Material.JUNGLE_SLAB);
        playerBlocks.add(Material.ACACIA_SLAB);
        playerBlocks.add(Material.DARK_OAK_SLAB);
        playerBlocks.add(Material.STONE_SLAB);
        playerBlocks.add(Material.COBBLESTONE_SLAB);
        playerBlocks.add(Material.BRICK_SLAB);
        playerBlocks.add(Material.STONE_BRICK_SLAB);
        playerBlocks.add(Material.NETHER_BRICK_SLAB);

        // Beds
        playerBlocks.add(Material.WHITE_BED);
        playerBlocks.add(Material.RED_BED);
        playerBlocks.add(Material.BLACK_BED);

        // Misc
        playerBlocks.add(Material.COBWEB);
        playerBlocks.add(Material.DISPENSER);
        playerBlocks.add(Material.NOTE_BLOCK);
        playerBlocks.add(Material.STICKY_PISTON);
        playerBlocks.add(Material.PISTON);
        playerBlocks.add(Material.BRICKS);
        playerBlocks.add(Material.OBSIDIAN);
        playerBlocks.add(Material.SPAWNER);
        playerBlocks.add(Material.FARMLAND);
        playerBlocks.add(Material.ENCHANTING_TABLE);
        playerBlocks.add(Material.BREWING_STAND);
        playerBlocks.add(Material.CAULDRON);
        playerBlocks.add(Material.ENDER_CHEST);
        playerBlocks.add(Material.BEACON);
        playerBlocks.add(Material.ANVIL);
        playerBlocks.add(Material.HOPPER);
        playerBlocks.add(Material.DROPPER);

        // Nether-specific
        if (environment == Environment.NETHER) {
            playerBlocks.remove(Material.FIRE); // Fire is natural in nether
            playerBlocks.add(Material.GLOWSTONE);
        }

        return playerBlocks;
    }
}
