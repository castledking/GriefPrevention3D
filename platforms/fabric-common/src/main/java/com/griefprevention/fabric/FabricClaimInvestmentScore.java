package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Approximates Paper's {@code Claim#getPlayerInvestmentScore} for UnusedClaimDays expiration.
 * Scans claim blocks and counts "player-placed" materials similarly to restore-nature's player block set.
 */
final class FabricClaimInvestmentScore
{
    private FabricClaimInvestmentScore()
    {
    }

    static long score(@NotNull ServerLevel level, @NotNull ClaimSnapshot claim, boolean creativeWorld)
    {
        ClaimBounds bounds = claim.bounds();
        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minZ = bounds.minZ();
        int maxZ = bounds.maxZ();
        int scanBottom = Math.max(level.getMinY(), bounds.minY());
        int scanTop = level.getMaxY();
        int deepCutoff = level.getSeaLevel() - 5;

        double score = 0.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                // Skip cells outside shaped claims.
                if (bounds.isShaped() && !bounds.containsColumn(x, z))
                {
                    continue;
                }
                for (int y = scanBottom; y < scanTop; y++)
                {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.liquid())
                    {
                        continue;
                    }
                    if (!isPlayerBlock(state, level))
                    {
                        continue;
                    }
                    if (state.is(Blocks.CHEST) && !creativeWorld)
                    {
                        score += 10.0D;
                    }
                    else if (creativeWorld && state.is(Blocks.LAVA))
                    {
                        score -= 10.0D;
                    }
                    else
                    {
                        score += y < deepCutoff ? 0.5D : 1.0D;
                    }
                }
            }
        }
        return (long) score;
    }

    /** Conservative subset of Paper's player-placed block detection, aligned with Fabric restore-nature heuristics. */
    private static boolean isPlayerBlock(@NotNull BlockState state, @NotNull ServerLevel level)
    {
        Block block = state.getBlock();

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.BARREL) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)
                || state.is(Blocks.HOPPER) || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.LADDER)
                || state.is(Blocks.RAIL) || state.is(Blocks.POWERED_RAIL) || state.is(Blocks.DETECTOR_RAIL)
                || state.is(Blocks.ACTIVATOR_RAIL) || state.is(Blocks.TNT) || state.is(Blocks.BOOKSHELF)
                || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.CHISELED_STONE_BRICKS)
                || state.is(Blocks.BRICKS) || state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.GLASS) || state.is(Blocks.IRON_BARS)
                || state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.BREWING_STAND) || state.is(Blocks.CAULDRON)
                || state.is(Blocks.BEACON) || state.is(Blocks.SPAWNER) || state.is(Blocks.NOTE_BLOCK)
                || state.is(Blocks.JUKEBOX) || state.is(Blocks.IRON_DOOR)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.BEDROCK))
        {
            return true;
        }

        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.BEDS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.STAIRS) || state.is(BlockTags.WALLS) || state.is(BlockTags.SIGNS)
                || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.IMPERMEABLE))
        {
            return true;
        }

        // Common crafted/placed utility blocks
        if (state.is(Blocks.GLASS_PANE) || state.is(Blocks.STICKY_PISTON) || state.is(Blocks.PISTON)
                || state.is(Blocks.OBSERVER) || state.is(Blocks.LEVER) || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.COAL_BLOCK) || state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.NETHERITE_BLOCK))
        {
            return true;
        }

        // Exclude obvious natural terrain blocks
        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.NETHERRACK) || state.is(Blocks.END_STONE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.WATER))
        {
            return false;
        }

        // Nether natural glowstone should not be treated as player placed in general scans.
        if (level.dimension() == net.minecraft.world.level.Level.NETHER && state.is(Blocks.GLOWSTONE))
        {
            return false;
        }

        return false;
    }
}
