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

import com.griefprevention.compat.BlockDataCompat;
import com.griefprevention.compat.MaterialCompat;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Locale;
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
    @SuppressWarnings("unused")
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
        this.notAllowedToHang = EnumSet.noneOf(Material.class);
        addMaterials(this.notAllowedToHang, "DIRT", "SNOW");
        addMaterials(this.notAllowedToHang, "SHORT_GRASS", "LONG_GRASS");
        addMaterials(this.notAllowedToHang, "TALL_GRASS", "DOUBLE_PLANT");
        addMaterials(this.notAllowedToHang, "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "LOG");
        addMaterials(this.notAllowedToHang, "ACACIA_LOG", "DARK_OAK_LOG", "LOG_2");
        try {
            this.notAllowedToHang.add(Material.valueOf("MANGROVE_LOG"));
        } catch (IllegalArgumentException e) {
            // 1.8.8: MANGROVE_LOG doesn't exist
        }
        try {
            this.notAllowedToHang.add(Material.valueOf("CHERRY_LOG"));
        } catch (IllegalArgumentException e) {
            // 1.8.8: CHERRY_LOG doesn't exist
        }

        if (this.aggressiveMode) {
            try {
                this.notAllowedToHang.add(Material.valueOf("GRASS_BLOCK"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: use GRASS
                try {
                    this.notAllowedToHang.add(Material.valueOf("GRASS"));
                } catch (IllegalArgumentException e2) {
                    // Neither exists, skip
                }
            }
            this.notAllowedToHang.add(Material.STONE);
        }

        // Initialize player-placed blocks
        @SuppressWarnings("null")
        Set<Material> playerBlocks = EnumSet.noneOf(Material.class);
        playerBlocks.addAll(getPlayerBlocks(this.environment, this.biome));
        this.playerBlocks = playerBlocks;

        // In aggressive or creative mode, also treat these blocks as player-placed
        if (this.aggressiveMode || this.creativeMode) {
            this.playerBlocks.add(Material.IRON_ORE);
            try {
                this.playerBlocks.add(Material.valueOf("DEEPSLATE_IRON_ORE"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: DEEPSLATE_IRON_ORE doesn't exist
            }
            this.playerBlocks.add(Material.GOLD_ORE);
            try {
                this.playerBlocks.add(Material.valueOf("DEEPSLATE_GOLD_ORE"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: DEEPSLATE_GOLD_ORE doesn't exist
            }
            this.playerBlocks.add(Material.DIAMOND_ORE);
            try {
                this.playerBlocks.add(Material.valueOf("DEEPSLATE_DIAMOND_ORE"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: DEEPSLATE_DIAMOND_ORE doesn't exist
            }
            this.playerBlocks.add(Material.MELON);
            this.playerBlocks.add(Material.MELON_STEM);
            this.playerBlocks.add(Material.BEDROCK);
            this.playerBlocks.add(Material.COAL_ORE);
            try {
                this.playerBlocks.add(Material.valueOf("DEEPSLATE_COAL_ORE"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: DEEPSLATE_COAL_ORE doesn't exist
            }
            this.playerBlocks.add(Material.PUMPKIN);
            this.playerBlocks.add(Material.PUMPKIN_STEM);
        }

        if (this.aggressiveMode) {
            addMaterials(this.playerBlocks, "OAK_LEAVES", "LEAVES");
            addMaterials(this.playerBlocks, "SPRUCE_LEAVES", "LEAVES");
            addMaterials(this.playerBlocks, "BIRCH_LEAVES", "LEAVES");
            addMaterials(this.playerBlocks, "JUNGLE_LEAVES", "LEAVES");
            addMaterials(this.playerBlocks, "ACACIA_LEAVES", "LEAVES_2");
            addMaterials(this.playerBlocks, "DARK_OAK_LEAVES", "LEAVES_2");
            try {
                this.playerBlocks.add(Material.valueOf("MANGROVE_LEAVES"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: MANGROVE_LEAVES doesn't exist
            }
            try {
                this.playerBlocks.add(Material.valueOf("CHERRY_LEAVES"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: CHERRY_LEAVES doesn't exist
            }
            addMaterials(this.playerBlocks, "OAK_LOG", "LOG");
            addMaterials(this.playerBlocks, "SPRUCE_LOG", "LOG");
            addMaterials(this.playerBlocks, "BIRCH_LOG", "LOG");
            addMaterials(this.playerBlocks, "JUNGLE_LOG", "LOG");
            addMaterials(this.playerBlocks, "ACACIA_LOG", "LOG_2");
            addMaterials(this.playerBlocks, "DARK_OAK_LOG", "LOG_2");
            try {
                this.playerBlocks.add(Material.valueOf("MANGROVE_LOG"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: MANGROVE_LOG doesn't exist
            }
            try {
                this.playerBlocks.add(Material.valueOf("CHERRY_LOG"));
            } catch (IllegalArgumentException e) {
                // 1.8.8: CHERRY_LOG doesn't exist
            }
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
                            block.blockData = BlockDataCompat.createBlockData(Material.AIR);
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
                        block.blockData = BlockDataCompat.createBlockData(Material.AIR);
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
                                block.blockData = BlockDataCompat.createBlockData(Material.AIR);
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
                                block.blockData = BlockDataCompat.createBlockData(Material.AIR);
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
            if (!CompatUtil.isCartesian(face)) {
                continue;
            }

            BlockSnapshot adjacent = getRelativeSnapshot(x, y, z, face);
            if (adjacent == null || !adjacent.protectedFromRestore) {
                continue;
            }

            if (adjacent.blockData != null && BlockDataCompat.isWallSignFromBlockData(adjacent.blockData)) {
                BlockFace facing = BlockDataCompat.getWallSignFacingFromBlockData(adjacent.blockData);
                if (facing == face) {
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
                            block.blockData = BlockDataCompat.createBlockData(fillMaterial);
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
                                block.blockData = BlockDataCompat.createBlockData(surfaceMaterial);

                                // Also fix the block below if needed
                                if (y > miny) {
                                    BlockSnapshot blockBelow = snapshots[x][y - 1][z];
                                    if (blockBelow != null && blockBelow.material == Material.STONE) {
                                        blockBelow.material = underMaterial;
                                        blockBelow.blockData = BlockDataCompat.createBlockData(underMaterial);
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
            return firstMaterial(Material.STONE, "END_STONE", "ENDER_STONE");
        }
        return Material.DIRT;
    }

    private Material getSurfaceMaterial() {
        if (environment == Environment.NETHER) {
            return Material.NETHERRACK;
        } else if (environment == Environment.THE_END) {
            return firstMaterial(Material.STONE, "END_STONE", "ENDER_STONE");
        }

        // Check biome for appropriate surface
        String biomeName = getBiomeName(biome);
        if (biomeName.contains("DESERT") || biomeName.contains("BEACH")) {
            return Material.SAND;
        } else if (biomeName.contains("BADLANDS") || biomeName.contains("MESA")) {
            return firstMaterial(Material.SAND, "RED_SAND");
        } else if (biomeName.contains("MUSHROOM")) {
            return firstMaterial(Material.DIRT, "MYCELIUM", "MYCEL");
        }

        try {
            return Material.valueOf("GRASS_BLOCK");
        } catch (IllegalArgumentException e) {
            // 1.8.8: use GRASS
            try {
                return Material.valueOf("GRASS");
            } catch (IllegalArgumentException e2) {
                return Material.DIRT; // Fallback
            }
        }
    }

    private Material getUnderSurfaceMaterial() {
        if (environment == Environment.NETHER) {
            return Material.NETHERRACK;
        } else if (environment == Environment.THE_END) {
            return firstMaterial(Material.STONE, "END_STONE", "ENDER_STONE");
        }

        String biomeName = getBiomeName(biome);
        if (biomeName.contains("DESERT") || biomeName.contains("BEACH")) {
            return Material.SAND;
        } else if (biomeName.contains("BADLANDS") || biomeName.contains("MESA")) {
            return firstMaterial(Material.SAND, "RED_SAND");
        }

        return Material.DIRT;
    }

    /**
     * Gets the set of materials considered to be player-placed based on environment and biome
     */
    @SuppressWarnings("null")
    /**
     * Add every material that exists on this server under one of the given names.
     * The modern name comes first with older aliases after it, e.g. OAK_LOG was LOG before 1.13.
     * Naming the constants directly would throw NoSuchFieldError on versions that lack them.
     */
    private static void addMaterials(Set<Material> materials, String... names) {
        for (String name : names) {
            Material material = MaterialCompat.get(name);
            if (material != null) {
                materials.add(material);
            }
        }
    }

    /**
     * Resolve the first of the given material names that exists on this server.
     */
    private static Material firstMaterial(Material fallback, String... names) {
        for (String name : names) {
            Material material = MaterialCompat.get(name);
            if (material != null) {
                return material;
            }
        }
        return fallback;
    }

    /**
     * Returns a version-independent biome label. Calling Biome#name directly emits an
     * invokeinterface instruction on current APIs, but Biome is a class on Bukkit 1.8.
     */
    private static String getBiomeName(Biome biome) {
        return biome == null ? "" : String.valueOf((Object) biome).toUpperCase(Locale.ROOT);
    }

    public static Set<Material> getPlayerBlocks(Environment environment, Biome biome) {
        Set<Material> playerBlocks = EnumSet.noneOf(Material.class);

        // Common player blocks across all environments
        playerBlocks.add(Material.FIRE);
        playerBlocks.add(Material.BEDROCK);
        playerBlocks.add(Material.COBBLESTONE);
        playerBlocks.add(Material.TORCH);
        addMaterials(playerBlocks, "WALL_TORCH", "TORCH");
        playerBlocks.add(Material.LADDER);
        addMaterials(playerBlocks, "CRAFTING_TABLE", "WORKBENCH");
        playerBlocks.add(Material.FURNACE);
        playerBlocks.add(Material.CHEST);
        playerBlocks.add(Material.TRAPPED_CHEST);
        addMaterials(playerBlocks, "OAK_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "SPRUCE_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "BIRCH_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "JUNGLE_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "ACACIA_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "DARK_OAK_SIGN", "SIGN_POST", "SIGN");
        addMaterials(playerBlocks, "OAK_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "SPRUCE_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "BIRCH_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "JUNGLE_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "ACACIA_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "DARK_OAK_WALL_SIGN", "WALL_SIGN");
        addMaterials(playerBlocks, "OAK_FENCE", "FENCE");
        addMaterials(playerBlocks, "NETHER_BRICK_FENCE", "NETHER_FENCE");
        playerBlocks.add(Material.GLASS);
        addMaterials(playerBlocks, "GLASS_PANE", "THIN_GLASS");
        addMaterials(playerBlocks, "OAK_DOOR", "WOODEN_DOOR", "WOOD_DOOR");
        playerBlocks.add(Material.IRON_DOOR);
        addMaterials(playerBlocks, "RAIL", "RAILS");
        playerBlocks.add(Material.POWERED_RAIL);
        playerBlocks.add(Material.DETECTOR_RAIL);
        playerBlocks.add(Material.ACTIVATOR_RAIL);
        playerBlocks.add(Material.TNT);
        playerBlocks.add(Material.BOOKSHELF);
        playerBlocks.add(Material.JACK_O_LANTERN);
        addMaterials(playerBlocks, "STONE_BRICKS", "SMOOTH_BRICK");
        addMaterials(playerBlocks, "MOSSY_STONE_BRICKS", "SMOOTH_BRICK");
        addMaterials(playerBlocks, "CRACKED_STONE_BRICKS", "SMOOTH_BRICK");
        addMaterials(playerBlocks, "CHISELED_STONE_BRICKS", "SMOOTH_BRICK");

        // All wool colors
        addMaterials(playerBlocks, "WHITE_WOOL", "WOOL");
        addMaterials(playerBlocks, "ORANGE_WOOL", "WOOL");
        addMaterials(playerBlocks, "MAGENTA_WOOL", "WOOL");
        addMaterials(playerBlocks, "LIGHT_BLUE_WOOL", "WOOL");
        addMaterials(playerBlocks, "YELLOW_WOOL", "WOOL");
        addMaterials(playerBlocks, "LIME_WOOL", "WOOL");
        addMaterials(playerBlocks, "PINK_WOOL", "WOOL");
        addMaterials(playerBlocks, "GRAY_WOOL", "WOOL");
        addMaterials(playerBlocks, "LIGHT_GRAY_WOOL", "WOOL");
        addMaterials(playerBlocks, "CYAN_WOOL", "WOOL");
        addMaterials(playerBlocks, "PURPLE_WOOL", "WOOL");
        addMaterials(playerBlocks, "BLUE_WOOL", "WOOL");
        addMaterials(playerBlocks, "BROWN_WOOL", "WOOL");
        addMaterials(playerBlocks, "GREEN_WOOL", "WOOL");
        addMaterials(playerBlocks, "RED_WOOL", "WOOL");
        addMaterials(playerBlocks, "BLACK_WOOL", "WOOL");

        // Planks and slabs
        addMaterials(playerBlocks, "OAK_PLANKS", "WOOD");
        addMaterials(playerBlocks, "SPRUCE_PLANKS", "WOOD");
        addMaterials(playerBlocks, "BIRCH_PLANKS", "WOOD");
        addMaterials(playerBlocks, "JUNGLE_PLANKS", "WOOD");
        addMaterials(playerBlocks, "ACACIA_PLANKS", "WOOD");
        addMaterials(playerBlocks, "DARK_OAK_PLANKS", "WOOD");
        addMaterials(playerBlocks, "OAK_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "SPRUCE_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "BIRCH_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "JUNGLE_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "ACACIA_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "DARK_OAK_SLAB", "WOOD_STEP");
        addMaterials(playerBlocks, "STONE_SLAB", "STEP");
        addMaterials(playerBlocks, "COBBLESTONE_SLAB", "STEP");
        addMaterials(playerBlocks, "BRICK_SLAB", "STEP");
        addMaterials(playerBlocks, "STONE_BRICK_SLAB", "STEP");
        addMaterials(playerBlocks, "NETHER_BRICK_SLAB", "STEP");

        // Beds
        addMaterials(playerBlocks, "WHITE_BED", "BED_BLOCK");
        addMaterials(playerBlocks, "RED_BED", "BED_BLOCK");
        addMaterials(playerBlocks, "BLACK_BED", "BED_BLOCK");

        // Misc
        addMaterials(playerBlocks, "COBWEB", "WEB");
        playerBlocks.add(Material.DISPENSER);
        playerBlocks.add(Material.NOTE_BLOCK);
        addMaterials(playerBlocks, "STICKY_PISTON", "PISTON_STICKY_BASE");
        addMaterials(playerBlocks, "PISTON", "PISTON_BASE");
        addMaterials(playerBlocks, "BRICKS", "BRICK");
        playerBlocks.add(Material.OBSIDIAN);
        addMaterials(playerBlocks, "SPAWNER", "MOB_SPAWNER");
        playerBlocks.addAll(MaterialCompat.availableSet("FARMLAND", "SOIL"));
        addMaterials(playerBlocks, "ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        try {
            playerBlocks.add(Material.valueOf("BREWING_STAND"));
        } catch (IllegalArgumentException e) {
            // 1.8.8: BREWING_STAND doesn't exist
        }
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
