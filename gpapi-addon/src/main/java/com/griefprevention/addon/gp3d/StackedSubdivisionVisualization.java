package com.griefprevention.addon.gp3d;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockBoundaryRenderer;
import com.griefprevention.visualization.BlockBoundaryVisualization;
import com.griefprevention.visualization.BlockElement;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

final class StackedSubdivisionVisualization extends BlockBoundaryVisualization
{
    private static final int STEP = 10;
    private static final int DISPLAY_RADIUS = 75;

    private final boolean waterTransparent;

    StackedSubdivisionVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height)
    {
        super(world, visualizeFrom, height, STEP, DISPLAY_RADIUS);
        this.waterTransparent = visualizeFrom.toBlock(world).getType() == Material.WATER;
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addCornerElements(@NotNull Boundary boundary)
    {
        BlockData data = getRenderer(boundary).createCornerBlockData(boundary);
        return isStacked(boundary) ? addExactBlockElement(data) : addSurfaceBlockElement(data);
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addSideElements(@NotNull Boundary boundary)
    {
        BlockData data = getRenderer(boundary).createSideBlockData(boundary);
        return isStacked(boundary) ? addExactBlockElement(data) : addSurfaceBlockElement(data);
    }

    @Override
    protected void draw(@NotNull Player player, @NotNull Boundary boundary)
    {
        if (isStacked(boundary))
        {
            drawStackedBoundary(player, boundary);
            return;
        }

        super.draw(player, boundary);
    }

    private void drawStackedBoundary(@NotNull Player player, @NotNull Boundary boundary)
    {
        BoundingBox area = boundary.bounds();
        // Keep horizontal culling for performance, but do not vertically cull by eye-height radius.
        // Vertical culling can hide the top plane of tall stacked claims.
        BoundingBox displayZone = new BoundingBox(
                new IntVector(visualizeFrom.x() - DISPLAY_RADIUS, area.getMinY(), visualizeFrom.z() - DISPLAY_RADIUS),
                new IntVector(visualizeFrom.x() + DISPLAY_RADIUS, area.getMaxY(), visualizeFrom.z() + DISPLAY_RADIUS))
                .intersection(area);
        if (displayZone == null)
        {
            return;
        }

        Consumer<@NotNull IntVector> addCorner = addCornerElements(boundary);
        Consumer<@NotNull IntVector> addSide = addSideElements(boundary);
        int minY = area.getMinY();
        int maxY = area.getMaxY();

        renderPlane(area, displayZone, minY, addCorner, addSide);
        if (maxY != minY)
        {
            renderPlane(area, displayZone, maxY, addCorner, addSide);
            renderVerticalIndicators(area, displayZone, minY + 1, maxY - 1, addSide);
        }
    }

    private void renderPlane(
            @NotNull BoundingBox area,
            @NotNull BoundingBox displayZone,
            int y,
            @NotNull Consumer<@NotNull IntVector> addCorner,
            @NotNull Consumer<@NotNull IntVector> addSide)
    {
        for (int x = Math.max(area.getMinX() + STEP, displayZone.getMinX()); x < area.getMaxX() - STEP / 2 && x < displayZone.getMaxX(); x += STEP)
        {
            addDisplayed3d(displayZone, new IntVector(x, y, area.getMaxZ()), addSide);
            addDisplayed3d(displayZone, new IntVector(x, y, area.getMinZ()), addSide);
        }

        if (area.getLength() > 2)
        {
            addDisplayed3d(displayZone, new IntVector(area.getMinX() + 1, y, area.getMaxZ()), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMinX() + 1, y, area.getMinZ()), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMaxX() - 1, y, area.getMaxZ()), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMaxX() - 1, y, area.getMinZ()), addSide);
        }

        for (int z = Math.max(area.getMinZ() + STEP, displayZone.getMinZ()); z < area.getMaxZ() - STEP / 2 && z < displayZone.getMaxZ(); z += STEP)
        {
            addDisplayed3d(displayZone, new IntVector(area.getMinX(), y, z), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMaxX(), y, z), addSide);
        }

        if (area.getWidth() > 2)
        {
            addDisplayed3d(displayZone, new IntVector(area.getMinX(), y, area.getMinZ() + 1), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMaxX(), y, area.getMinZ() + 1), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMinX(), y, area.getMaxZ() - 1), addSide);
            addDisplayed3d(displayZone, new IntVector(area.getMaxX(), y, area.getMaxZ() - 1), addSide);
        }

        addDisplayed3d(displayZone, new IntVector(area.getMinX(), y, area.getMaxZ()), addCorner);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), y, area.getMaxZ()), addCorner);
        addDisplayed3d(displayZone, new IntVector(area.getMinX(), y, area.getMinZ()), addCorner);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), y, area.getMinZ()), addCorner);
    }

    private void renderVerticalIndicators(
            @NotNull BoundingBox area,
            @NotNull BoundingBox displayZone,
            int minY,
            int maxY,
            @NotNull Consumer<@NotNull IntVector> addSide)
    {
        if (minY > maxY)
        {
            return;
        }

        addDisplayed3d(displayZone, new IntVector(area.getMinX(), minY, area.getMinZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMinX(), maxY, area.getMinZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMinX(), minY, area.getMaxZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMinX(), maxY, area.getMaxZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), minY, area.getMinZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), maxY, area.getMinZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), minY, area.getMaxZ()), addSide);
        addDisplayed3d(displayZone, new IntVector(area.getMaxX(), maxY, area.getMaxZ()), addSide);
    }

    private void addDisplayed3d(
            @NotNull BoundingBox displayZone,
            @NotNull IntVector coordinate,
            @NotNull Consumer<@NotNull IntVector> addElement)
    {
        if (coordinate.x() >= displayZone.getMinX()
                && coordinate.x() <= displayZone.getMaxX()
                && coordinate.y() >= displayZone.getMinY()
                && coordinate.y() <= displayZone.getMaxY()
                && coordinate.z() >= displayZone.getMinZ()
                && coordinate.z() <= displayZone.getMaxZ()
                && coordinate.isChunkLoaded(world))
        {
            addElement.accept(coordinate);
        }
    }

    private boolean isStacked(@NotNull Boundary boundary)
    {
        return StackedSubdivisionStyle.KEY.equals(boundary.style().getKey());
    }

    private @NotNull BlockBoundaryRenderer getRenderer(@NotNull Boundary boundary)
    {
        BlockBoundaryRenderer renderer = boundary.style().getBlockRenderer();
        if (renderer != null)
        {
            return renderer;
        }

        return Objects.requireNonNull(VisualizationType.CLAIM.getBlockRenderer());
    }

    private @NotNull Consumer<@NotNull IntVector> addSurfaceBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            Block visibleLocation = getVisibleLocation(vector);
            elements.add(new ExampleFakeBlockElement(new IntVector(visibleLocation), visibleLocation.getBlockData(), fakeData));
        };
    }

    private @NotNull Consumer<@NotNull IntVector> addExactBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            Block visibleLocation = vector.toBlock(world);
            elements.add(new ExampleFakeBlockElement(new IntVector(visibleLocation), visibleLocation.getBlockData(), fakeData));
        };
    }

    private @NotNull Block getVisibleLocation(@NotNull IntVector vector)
    {
        Block block = vector.toBlock(world);
        BlockFace direction = isTransparent(block) ? BlockFace.DOWN : BlockFace.UP;

        while (block.getY() >= world.getMinHeight()
                && block.getY() < world.getMaxHeight() - 1
                && (!isTransparent(block.getRelative(BlockFace.UP)) || isTransparent(block)))
        {
            block = block.getRelative(direction);
        }

        return block;
    }

    private boolean isTransparent(@NotNull Block block)
    {
        Material material = block.getType();
        switch (material)
        {
            case WATER:
                return waterTransparent;
            case SNOW:
                return false;
            default:
                break;
        }

        if (material.isAir()
                || Tag.FENCES.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material)
                || Tag.SIGNS.isTagged(material)
                || Tag.WALLS.isTagged(material)
                || Tag.WALL_SIGNS.isTagged(material))
        {
            return true;
        }

        return material.isTransparent();
    }

    private static final class ExampleFakeBlockElement extends BlockElement
    {
        private final @NotNull BlockData realBlock;
        private final @NotNull BlockData visualizedBlock;

        private ExampleFakeBlockElement(
                @NotNull IntVector coordinate,
                @NotNull BlockData realBlock,
                @NotNull BlockData visualizedBlock)
        {
            super(coordinate);
            this.realBlock = realBlock;
            this.visualizedBlock = visualizedBlock;
        }

        @Override
        protected void draw(@NotNull Player player, @NotNull World world)
        {
            if (!getCoordinate().isChunkLoaded(world))
            {
                return;
            }

            player.sendBlockChange(getCoordinate().toLocation(world), visualizedBlock);
        }

        @Override
        protected void erase(@NotNull Player player, @NotNull World world)
        {
            player.sendBlockChange(getCoordinate().toLocation(world), realBlock);
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!super.equals(other)) return false;
            if (getClass() != other.getClass()) return false;
            ExampleFakeBlockElement that = (ExampleFakeBlockElement) other;
            return realBlock.equals(that.realBlock) && visualizedBlock.equals(that.visualizedBlock);
        }

        @Override
        public int hashCode()
        {
            return super.hashCode();
        }
    }
}
