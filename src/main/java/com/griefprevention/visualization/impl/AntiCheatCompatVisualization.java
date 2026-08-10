package com.griefprevention.visualization.impl;

import com.griefprevention.util.IntVector;
import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link FakeBlockVisualization} with maximum anti-cheat compatibility.
 */
public class AntiCheatCompatVisualization extends FakeBlockVisualization
{

    /**
     * Construct a new {@code AntiCheatCompatVisualization}.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     */
    public AntiCheatCompatVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height)
    {
        super(world, visualizeFrom, height);
    }

    @Override
    protected boolean isTransparent(@NotNull Block block)
    {
        if (isPathBlock(block.getType()))
        {
            return false;
        }

        // Decide transparency based on whether block physical bounding box occupies the entire block volume.
        return CompatUtil.hasPartialCollision(block);
    }

}
