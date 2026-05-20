package me.ryanhamshire.GriefPrevention;

import com.griefprevention.compat.MaterialCompat;
import com.griefprevention.compat.MaterialTagCompat;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.loot.Lootable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;
import java.util.function.Consumer;

//automatically extends a claim downward based on block types detected
public class AutoExtendClaimTask implements Runnable
{
    private static final @Nullable Material chainMaterial = resolveMaterial("IRON_CHAIN", "CHAIN");


    /**
     * Assemble information and schedule a task to update claim depth to include existing structures.
     *
     * @param claim the claim to extend the depth of
     */
    public static void scheduleAsync(@NotNull Claim claim)
    {
        // Skip 3D claims - they have explicit Y bounds set by player clicks
        // and should not be auto-extended downward
        if (claim.is3D()) return;

        Location lesserCorner = claim.getLesserBoundaryCorner();
        Location greaterCorner = claim.getGreaterBoundaryCorner();
        World world = lesserCorner.getWorld();

        if (world == null) return;

        int lowestLootableTile = lesserCorner.getBlockY();
        ArrayList<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int chunkX = lesserCorner.getBlockX() / 16; chunkX <= greaterCorner.getBlockX() / 16; chunkX++)
        {
            for (int chunkZ = lesserCorner.getBlockZ() / 16; chunkZ <= greaterCorner.getBlockZ() / 16; chunkZ++)
            {
                if (world.isChunkLoaded(chunkX, chunkZ))
                {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);

                    // If we're on the main thread, access to tile entities will speed up the process.
                    if (Bukkit.isPrimaryThread())
                    {
                        // Find the lowest non-natural storage block in the chunk.
                        // This way chests, barrels, etc. are always protected even if player block definitions are lacking.
                        {
                            OptionalInt lowestInChunk = Arrays.stream(chunk.getTileEntities())
                                    // Accept only Lootable tiles that do not have loot tables.
                                    // Naturally generated Lootables only have a loot table reference until the container is
                                    // accessed. On access the loot table is used to calculate the contents and removed.
                                    // This prevents claims from always extending over unexplored structures, spawners, etc.
                                    .filter(tile -> tile instanceof Lootable lootable && lootable.getLootTable() == null)
                                    // Return smallest value if present.
                                    .mapToInt(BlockState::getY)
                                    .min();
                            if (lowestInChunk.isPresent())
                            {
                                lowestLootableTile = Math.min(lowestLootableTile, lowestInChunk.getAsInt());
                            }
                        }
                    }

                    // Save a snapshot of the chunk for more detailed async block searching.
                    snapshots.add(chunk.getChunkSnapshot(false, true, false));
                }
            }
        }

        // Prefer Folia/Paper AsyncScheduler if available; fallback to Bukkit async scheduler
        final int finalLowestLootableTile = lowestLootableTile;
        try {
            // Bukkit.getAsyncScheduler().runNow(plugin, Consumer<ScheduledTask>)
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = (ignored) -> new AutoExtendClaimTask(claim, snapshots, world.getEnvironment(), finalLowestLootableTile).run();
            try {
                // new API may accept Runnable directly in some builds; try Consumer first
                asyncScheduler.getClass().getMethod("runNow", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                        .invoke(asyncScheduler, GriefPrevention.instance, consumer);
            } catch (NoSuchMethodException e) {
                // Fallback: try Runnable signature if present
                asyncScheduler.getClass().getMethod("runNow", org.bukkit.plugin.Plugin.class, java.lang.Runnable.class)
                        .invoke(asyncScheduler, GriefPrevention.instance, (Runnable) () -> new AutoExtendClaimTask(claim, snapshots, world.getEnvironment(), finalLowestLootableTile).run());
            }
        } catch (Throwable ignored) {
            // Non-Paper/Folia fallback
            SchedulerUtil.runAsyncNow(
                    GriefPrevention.instance,
                    new AutoExtendClaimTask(claim, snapshots, world.getEnvironment(), finalLowestLootableTile));
        }
    }

    private final Claim claim;
    private final ArrayList<ChunkSnapshot> chunks;
    private final Environment worldType;
    private final Map<Biome, Set<Material>> biomePlayerMaterials = new HashMap<>();
    private final int minY;
    private final int lowestExistingY;
    // Definitions of biomes where sand covers surfaces instead of grass.
    static final Set<NamespacedKey> SAND_SOIL_BIOMES = Set.of(
            NamespacedKey.minecraft("snowy_beach"),
            NamespacedKey.minecraft("beach"),
            NamespacedKey.minecraft("desert")
    );

    private AutoExtendClaimTask(
            @NotNull Claim claim,
            @NotNull ArrayList<@NotNull ChunkSnapshot> chunks,
            @NotNull Environment worldType,
            int lowestExistingY)
    {
        this.claim = claim;
        this.chunks = chunks;
        this.worldType = worldType;
        this.lowestExistingY = Math.min(lowestExistingY, claim.getLesserBoundaryCorner().getBlockY());
        World world = Objects.requireNonNull(claim.getLesserBoundaryCorner().getWorld());
        this.minY = Math.max(GriefPrevention.getWorldMinY(world), GriefPrevention.instance.getMaxDepthForWorld(world));
    }

    @Override
    public void run()
    {
        int newY = this.getLowestBuiltY();
        if (newY < this.claim.getLesserBoundaryCorner().getBlockY())
        {
            SchedulerUtil.runLaterGlobal(GriefPrevention.instance, new ExecuteExtendClaimTask(claim, newY), 1);
        }
    }

    private int getLowestBuiltY()
    {
        int y = this.lowestExistingY;

        if (yTooSmall(y)) return this.minY;

        for (ChunkSnapshot chunk : this.chunks)
        {
            y = findLowerBuiltY(chunk, y);

            // If already at minimum Y, stop searching.
            if (yTooSmall(y)) return this.minY;
        }

        return y;
    }

    private int findLowerBuiltY(ChunkSnapshot chunkSnapshot, int y)
    {
        // Specifically not using yTooSmall here to allow protecting bottom layer.
        for (int newY = y - 1; newY >= this.minY; newY--)
        {
            y = scanLayerForPlayerColumn(chunkSnapshot, newY, y);
            // If we've hit minimum Y we're done searching.
            if (yTooSmall(y)) return this.minY;
        }
        return y;
    }

    private int scanLayerForPlayerColumn(ChunkSnapshot chunk, int layerY, int currentLowestY)
    {
        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                if (!isPlayerBlock(chunk, x, layerY, z)) continue;

                // Found a player block: find the bottom of the player-built column.
                return Math.min(currentLowestY, findBottomOfPlayerColumn(chunk, x, layerY, z));
            }
        }
        return currentLowestY;
    }

    private int findBottomOfPlayerColumn(ChunkSnapshot chunk, int x, int y, int z)
    {
        while (y > this.minY && isPlayerBlock(chunk, x, y - 1, z))
        {
            y--;
        }
        return y;
    }

    private boolean yTooSmall(int y)
    {
        return y <= this.minY;
    }

    private boolean isPlayerBlock(ChunkSnapshot chunkSnapshot, int x, int y, int z)
    {
        Material blockType = chunkSnapshot.getBlockType(x, y, z);
        Biome biome = chunkSnapshot.getBiome(x, y, z);

        return this.getBiomePlayerBlocks(biome).contains(blockType);
    }

    private Set<Material> getBiomePlayerBlocks(Biome biome)
    {
        return biomePlayerMaterials.computeIfAbsent(biome, newBiome ->
                {
                    Set<Material> playerBlocks = AutoExtendClaimTask.getPlayerBlocks(this.worldType, newBiome);
                    playerBlocks.removeAll(BlockEventHandler.TRASH_BLOCKS);
                    return playerBlocks;
                });
    }

    static Set<Material> getPlayerBlocks(Environment environment, Biome biome)
    {
        Set<Material> playerBlocks = new HashSet<>();
        addMaterialTags(playerBlocks,
                "ANVIL",
                "BANNERS",
                "BEACON_BASE_BLOCKS",
                "BEDS",
                "BUTTONS",
                "CAMPFIRES",
                "CANDLE_CAKES",
                "CANDLES",
                "WOOL_CARPETS",
                "CAULDRONS",
                "DOORS",
                "FENCE_GATES",
                "FENCES",
                "FIRE",
                "FLOWER_POTS",
                "IMPERMEABLE",
                "LOGS",
                "PLANKS",
                "PRESSURE_PLATES",
                "RAILS",
                "SHULKER_BOXES",
                "SIGNS",
                "SLABS",
                "STAIRS",
                "STONE_BRICKS",
                "TRAPDOORS",
                "WALLS",
                "WOOL");
        addMaterials(playerBlocks,
                "BOOKSHELF",
                "BREWING_STAND",
                "BRICK",
                "COBBLESTONE",
                "LAPIS_BLOCK",
                "DISPENSER",
                "NOTE_BLOCK",
                "STICKY_PISTON",
                "PISTON",
                "PISTON_HEAD",
                "PISTON_EXTENSION",
                "MOVING_PISTON",
                "PISTON_MOVING_PIECE",
                "WHEAT",
                "CROPS",
                "TNT",
                "MOSSY_COBBLESTONE",
                "TORCH",
                "CHEST",
                "REDSTONE_WIRE",
                "CRAFTING_TABLE",
                "WORKBENCH",
                "FURNACE",
                "LADDER",
                "SCAFFOLDING",
                "LEVER",
                "REDSTONE_TORCH",
                "REDSTONE_TORCH_ON",
                "REDSTONE_TORCH_OFF",
                "SNOW_BLOCK",
                "JUKEBOX",
                "NETHER_PORTAL",
                "PORTAL",
                "JACK_O_LANTERN",
                "CAKE",
                "REPEATER",
                "DIODE_BLOCK_ON",
                "DIODE_BLOCK_OFF",
                "MUSHROOM_STEM",
                "RED_MUSHROOM_BLOCK",
                "BROWN_MUSHROOM_BLOCK",
                "HUGE_MUSHROOM_1",
                "HUGE_MUSHROOM_2",
                "IRON_BARS",
                "GLASS_PANE",
                "MELON_STEM",
                "ENCHANTING_TABLE",
                "ENCHANTMENT_TABLE",
                "COBWEB",
                "WEB",
                "GRAVEL",
                "SANDSTONE",
                "ENDER_CHEST",
                "COMMAND_BLOCK",
                "COMMAND",
                "REPEATING_COMMAND_BLOCK",
                "CHAIN_COMMAND_BLOCK",
                "BEACON",
                "CARROT",
                "CARROTS",
                "POTATO",
                "POTATOES",
                "SKELETON_SKULL",
                "WITHER_SKELETON_SKULL",
                "CREEPER_HEAD",
                "ZOMBIE_HEAD",
                "PLAYER_HEAD",
                "DRAGON_HEAD",
                "SKULL",
                "SPONGE",
                "WHITE_STAINED_GLASS_PANE",
                "ORANGE_STAINED_GLASS_PANE",
                "MAGENTA_STAINED_GLASS_PANE",
                "LIGHT_BLUE_STAINED_GLASS_PANE",
                "YELLOW_STAINED_GLASS_PANE",
                "LIME_STAINED_GLASS_PANE",
                "PINK_STAINED_GLASS_PANE",
                "GRAY_STAINED_GLASS_PANE",
                "LIGHT_GRAY_STAINED_GLASS_PANE",
                "CYAN_STAINED_GLASS_PANE",
                "PURPLE_STAINED_GLASS_PANE",
                "BLUE_STAINED_GLASS_PANE",
                "BROWN_STAINED_GLASS_PANE",
                "GREEN_STAINED_GLASS_PANE",
                "RED_STAINED_GLASS_PANE",
                "BLACK_STAINED_GLASS_PANE",
                "STAINED_GLASS_PANE",
                "TRAPPED_CHEST",
                "COMPARATOR",
                "REDSTONE_COMPARATOR_ON",
                "REDSTONE_COMPARATOR_OFF",
                "DAYLIGHT_DETECTOR",
                "DAYLIGHT_DETECTOR_INVERTED",
                "REDSTONE_BLOCK",
                "HOPPER",
                "QUARTZ_BLOCK",
                "DROPPER",
                "SLIME_BLOCK",
                "PRISMARINE",
                "HAY_BLOCK",
                "SEA_LANTERN",
                "COAL_BLOCK",
                "REDSTONE_LAMP",
                "REDSTONE_LAMP_ON",
                "REDSTONE_LAMP_OFF",
                "RED_NETHER_BRICKS",
                "POLISHED_ANDESITE",
                "POLISHED_DIORITE",
                "POLISHED_GRANITE",
                "POLISHED_BASALT",
                "POLISHED_DEEPSLATE",
                "DEEPSLATE_BRICKS",
                "CRACKED_DEEPSLATE_BRICKS",
                "DEEPSLATE_TILES",
                "CRACKED_DEEPSLATE_TILES",
                "CHISELED_DEEPSLATE",
                "RAW_COPPER_BLOCK",
                "RAW_IRON_BLOCK",
                "RAW_GOLD_BLOCK",
                "LIGHTNING_ROD",
                "DECORATED_POT");
    
        //these are unnatural in the nether and end
        if (environment != Environment.NORMAL && environment != Environment.CUSTOM)
        {
            addMaterialTags(playerBlocks,
                    "BASE_STONE_OVERWORLD",
                    "DIRT",
                    "SAND");
        }
    
        //these are unnatural in the standard world, but not in the nether
        if (environment != Environment.NETHER)
        {
            addMaterialTags(playerBlocks,
                    "NYLIUM",
                    "WART_BLOCKS",
                    "BASE_STONE_NETHER",
                    "CHAINS");
            addMaterials(playerBlocks,
                    "POLISHED_BLACKSTONE",
                    "CHISELED_POLISHED_BLACKSTONE",
                    "CRACKED_POLISHED_BLACKSTONE_BRICKS",
                    "GILDED_BLACKSTONE",
                    "BONE_BLOCK",
                    "SOUL_SAND",
                    "SOUL_SOIL",
                    "GLOWSTONE",
                    "NETHER_BRICK",
                    "MAGMA_BLOCK",
                    "ANCIENT_DEBRIS");
            if (chainMaterial != null)
            {
                playerBlocks.add(chainMaterial);
            }
            addMaterials(playerBlocks,
                    "SHROOMLIGHT",
                    "NETHER_GOLD_ORE",
                    "NETHER_SPROUTS",
                    "CRIMSON_FUNGUS",
                    "CRIMSON_ROOTS",
                    "NETHER_WART_BLOCK",
                    "WEEPING_VINES",
                    "WEEPING_VINES_PLANT",
                    "WARPED_FUNGUS",
                    "WARPED_ROOTS",
                    "WARPED_WART_BLOCK",
                    "TWISTING_VINES",
                    "TWISTING_VINES_PLANT");
        }
        //blocks from tags that are natural in the nether
        else
        {
            removeMaterials(playerBlocks,
                    "CRIMSON_STEM",
                    "CRIMSON_HYPHAE",
                    "NETHER_BRICK_FENCE",
                    "NETHER_BRICK_STAIRS",
                    "SOUL_FIRE",
                    "WARPED_STEM",
                    "WARPED_HYPHAE");
        }
    
        //these are unnatural in the standard and nether worlds, but not in the end
        if (environment != Environment.THE_END)
        {
            addMaterials(playerBlocks,
                    "CHORUS_PLANT",
                    "CHORUS_FLOWER",
                    "END_ROD",
                    "END_STONE",
                    "END_STONE_BRICKS",
                    "END_BRICKS",
                    "OBSIDIAN",
                    "PURPUR_BLOCK",
                    "PURPUR_PILLAR");
        }
        //blocks from tags that are natural in the end
        else
        {
            removeMaterials(playerBlocks,
                    "PURPUR_SLAB",
                    "PURPUR_STAIRS");
        }
    
        //these are unnatural in sandy biomes, but not elsewhere
        @SuppressWarnings("deprecation")
        var biomeKey = biome.getKey();
        if (SAND_SOIL_BIOMES.contains(biomeKey) || environment != Environment.NORMAL)
        {
            addMaterialTags(playerBlocks, "LEAVES");
        }
        //blocks from tags that are natural in non-sandy normal biomes
        else
        {
            removeMaterials(playerBlocks,
                    "OAK_LOG",
                    "SPRUCE_LOG",
                    "BIRCH_LOG",
                    "JUNGLE_LOG",
                    "ACACIA_LOG",
                    "DARK_OAK_LOG",
                    "LOG",
                    "LOG_2");
        }
    
        return playerBlocks;
    }

    private static @Nullable Material resolveMaterial(@NotNull String... names)
    {
        for (String name : names)
        {
            Material material = MaterialCompat.get(name);
            if (material != null)
            {
                return material;
            }
        }

        return null;
    }

    private static void addMaterials(@NotNull Set<Material> materials, @NotNull String... names)
    {
        materials.addAll(MaterialCompat.availableSet(names));
    }

    private static void addMaterialTags(@NotNull Set<Material> materials, @NotNull String... tagNames)
    {
        for (String tagName : tagNames)
        {
            materials.addAll(MaterialTagCompat.values(tagName));
        }
    }

    private static void removeMaterials(@NotNull Set<Material> materials, @NotNull String... names)
    {
        materials.removeAll(MaterialCompat.availableSet(names));
    }

    //runs in the main execution thread, where it can safely change claims and save those changes
    private record ExecuteExtendClaimTask(Claim claim, int newY) implements Runnable
    {
        @Override
        public void run()
        {
            GriefPrevention.instance.dataStore.extendClaim(claim, newY);
        }
    }

}
