package com.griefprevention.visualization.impl;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockBoundaryRenderer;
import com.griefprevention.visualization.BlockBoundaryVisualization;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link BoundaryVisualization} implementation that displays clientside blocks along
 * {@link com.griefprevention.visualization.Boundary Boundaries}.
 */
public class FakeBlockVisualization extends BlockBoundaryVisualization
{

    protected final boolean waterTransparent;

    /**
     * Construct a new {@code FakeBlockVisualization}.
     *
     * @param world the {@link World} being visualized in
     * @param visualizeFrom the {@link IntVector} representing the world coordinate being visualized from
     * @param height the height of the visualization
     */
    public FakeBlockVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height) {
        super(world, visualizeFrom, height);

        // Water is considered transparent based on whether the visualization is initiated in water.
        waterTransparent = visualizeFrom.toBlock(world).getType() == Material.WATER;
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addCornerElements(@NotNull Boundary boundary)
    {
        return addBlockElement(getBlockRenderer(boundary).createCornerBlockData(boundary));
    }


    @Override
    protected @NotNull Consumer<@NotNull IntVector> addSideElements(@NotNull Boundary boundary)
    {
        return addBlockElement(getBlockRenderer(boundary).createSideBlockData(boundary));
    }

    private @NotNull BlockBoundaryRenderer getBlockRenderer(@NotNull Boundary boundary)
    {
        BlockBoundaryRenderer renderer = boundary.style().getBlockRenderer();
        if (renderer != null)
        {
            return renderer;
        }

        GriefPrevention.instance.getLogger().warning(
                "Visualization style " + boundary.style().getKey() + " has no block renderer; falling back to CLAIM.");
        return Objects.requireNonNull(VisualizationType.CLAIM.getBlockRenderer());
    }

    /**
     * Create a {@link Consumer} that adds an appropriate {@link FakeBlockElement} for the given {@link IntVector}.
     *
     * @param fakeData the fake {@link BlockData}
     * @return the function for determining a visible fake block location
     */
    private @NotNull Consumer<@NotNull IntVector> addBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            // Obtain visible location from starting point.
            Block visibleLocation = getVisibleLocation(vector);
            // Create an element using our fake data and the determined block's real data.
            elements.add(new FakeBlockElement(new IntVector(visibleLocation), visibleLocation.getBlockData(), fakeData));
        };
    }

    /**
     * Find a location that should be visible to players. This causes the visualization to "cling" to the ground.
     *
     * @param vector the {@link IntVector} of the display location
     * @return the located {@link Block}
     */
    private Block getVisibleLocation(@NotNull IntVector vector)
    {
        Block block = vector.toBlock(world);
        BlockFace direction = (isTransparent(block)) ? BlockFace.DOWN : BlockFace.UP;

        while (block.getY() >= world.getMinHeight() &&
                block.getY() < world.getMaxHeight() - 1 &&
                (!isTransparent(block.getRelative(BlockFace.UP)) || isTransparent(block)))
        {
            block = block.getRelative(direction);
        }

        return block;
    }

    /**
     * Helper method for determining if a {@link Block} is transparent from the top down.
     *
     * @param block the {@code Block}
     * @return true if transparent
     */
    protected boolean isTransparent(@NotNull Block block)
    {
        Material blockMaterial = block.getType();

        // Custom per-material definitions.
        switch (blockMaterial)
        {
            case WATER:
                return waterTransparent;
            case SNOW:
                return false;
        }

        if (blockMaterial.isAir()
                || Tag.FENCES.isTagged(blockMaterial)
                || Tag.FENCE_GATES.isTagged(blockMaterial)
                || Tag.SIGNS.isTagged(blockMaterial)
                || Tag.WALLS.isTagged(blockMaterial)
                || Tag.WALL_SIGNS.isTagged(blockMaterial))
            return true;

        return block.getType().isTransparent();
    }

}
