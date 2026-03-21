package com.griefprevention.addon.gp3d;

import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.BlockBoundaryRenderer;
import com.griefprevention.visualization.BlockBoundaryVisualization;
import com.griefprevention.visualization.BlockElement;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class GlowingStackedSubdivisionVisualization extends BlockBoundaryVisualization
{
    private static final int STEP = 10;
    private static final int DISPLAY_RADIUS = 75;
    private static final float OUTLINE_SCALE = 1.005f;
    private static final AxisAngle4f ROT_IDENTITY = new AxisAngle4f(0f, 0f, 1f, 0f);

    private static final Map<UUID, Map<DisplayPosition, BlockDisplay>> ACTIVE_DISPLAYS = new ConcurrentHashMap<>();

    private final @NotNull GP3DAddon plugin;
    private final boolean waterTransparent;
    private @Nullable UUID viewerId;

    GlowingStackedSubdivisionVisualization(
            @NotNull GP3DAddon plugin,
            @NotNull World world,
            @NotNull IntVector visualizeFrom,
            int height)
    {
        super(world, visualizeFrom, height, STEP, DISPLAY_RADIUS);
        this.plugin = plugin;
        this.waterTransparent = visualizeFrom.toBlock(world).getType() == Material.WATER;
    }

    @Override
    protected void apply(@NotNull Player player, @NotNull PlayerData playerData)
    {
        viewerId = player.getUniqueId();
        clearDisplaysForPlayer(viewerId);
        super.apply(player, playerData);
    }

    @Override
    public void revert(@Nullable Player player)
    {
        super.revert(player);
        UUID target = player != null ? player.getUniqueId() : viewerId;
        if (target != null)
        {
            clearDisplaysForPlayer(target);
        }
        viewerId = null;
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
            drawStackedBoundary(boundary);
            return;
        }

        super.draw(player, boundary);
    }

    private void drawStackedBoundary(@NotNull Boundary boundary)
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
            elements.add(new GlowingFakeBlockElement(
                    new IntVector(visibleLocation),
                    visibleLocation.getBlockData(),
                    fakeData));
        };
    }

    private @NotNull Consumer<@NotNull IntVector> addExactBlockElement(@NotNull BlockData fakeData)
    {
        return vector -> {
            Block visibleLocation = vector.toBlock(world);
            elements.add(new GlowingFakeBlockElement(
                    new IntVector(visibleLocation),
                    visibleLocation.getBlockData(),
                    fakeData));
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

    private @Nullable BlockDisplay spawnDisplay(
            @NotNull Player player,
            @NotNull World world,
            @NotNull IntVector coordinate,
            @NotNull BlockData blockData)
    {
        if (!player.isOnline() || !coordinate.isChunkLoaded(world))
        {
            return null;
        }

        Location location = coordinate.toLocation(world);
        if (!location.getChunk().isLoaded())
        {
            return null;
        }

        try
        {
            return world.spawn(location, BlockDisplay.class, spawned -> {
                spawned.setVisibleByDefault(false);
                try
                {
                    player.showEntity(plugin, spawned);
                }
                catch (Exception ignored)
                {
                    // Older server APIs may not support per-player visibility.
                }

                spawned.setGravity(false);
                spawned.setBlock(blockData);
                spawned.setGlowing(true);
                spawned.setBrightness(new Display.Brightness(12, 12));
                spawned.setShadowStrength(0.0f);
                spawned.setShadowRadius(0.0f);

                float scale = OUTLINE_SCALE;
                float offset = -(scale - 1.0f) / 2.0f;
                spawned.setTransformation(new Transformation(
                        new Vector3f(offset, offset, offset),
                        ROT_IDENTITY,
                        new Vector3f(scale, scale, scale),
                        ROT_IDENTITY));

                spawned.setViewRange(96);
                spawned.setInterpolationDuration(0);
                spawned.setGlowColorOverride(defaultGlowColor(blockData.getMaterial()));
            });
        }
        catch (Exception exception)
        {
            plugin.getLogger().warning("Failed to create glowing block display at "
                    + coordinate.x() + "," + coordinate.y() + "," + coordinate.z() + ": "
                    + exception.getMessage());
            return null;
        }
    }

    private static @NotNull Color defaultGlowColor(@NotNull Material material)
    {
        return switch (material)
        {
            case GOLD_BLOCK, GLOWSTONE -> Color.YELLOW;
            case IRON_BLOCK, WHITE_WOOL -> Color.WHITE;
            case DIAMOND_BLOCK -> Color.AQUA;
            case REDSTONE_ORE, NETHERRACK -> Color.RED;
            case PUMPKIN -> Color.ORANGE;
            case LIME_GLAZED_TERRACOTTA, EMERALD_BLOCK -> Color.LIME;
            default -> Color.YELLOW;
        };
    }

    static void clearDisplaysForPlayer(@NotNull UUID playerId)
    {
        Map<DisplayPosition, BlockDisplay> displays = ACTIVE_DISPLAYS.remove(playerId);
        if (displays == null || displays.isEmpty())
        {
            return;
        }

        displays.values().forEach(GlowingStackedSubdivisionVisualization::removeDisplayEntity);
    }

    static void clearAllDisplays()
    {
        for (UUID playerId : ACTIVE_DISPLAYS.keySet())
        {
            clearDisplaysForPlayer(playerId);
        }
    }

    private static void trackDisplay(
            @NotNull UUID playerId,
            @NotNull UUID worldId,
            @NotNull IntVector coordinate,
            @NotNull BlockDisplay display)
    {
        Map<DisplayPosition, BlockDisplay> playerDisplays =
                ACTIVE_DISPLAYS.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        DisplayPosition key = new DisplayPosition(worldId, coordinate.x(), coordinate.y(), coordinate.z());
        BlockDisplay previous = playerDisplays.put(key, display);
        if (previous != null && previous != display)
        {
            removeDisplayEntity(previous);
        }
    }

    private static void removeDisplay(
            @NotNull UUID playerId,
            @NotNull UUID worldId,
            @NotNull IntVector coordinate)
    {
        Map<DisplayPosition, BlockDisplay> playerDisplays = ACTIVE_DISPLAYS.get(playerId);
        if (playerDisplays == null)
        {
            return;
        }

        DisplayPosition key = new DisplayPosition(worldId, coordinate.x(), coordinate.y(), coordinate.z());
        BlockDisplay removed = playerDisplays.remove(key);
        if (removed != null)
        {
            removeDisplayEntity(removed);
        }
        if (playerDisplays.isEmpty())
        {
            ACTIVE_DISPLAYS.remove(playerId, playerDisplays);
        }
    }

    private static void removeDisplayEntity(@NotNull BlockDisplay display)
    {
        try
        {
            if (display.isValid())
            {
                display.remove();
            }
        }
        catch (Exception ignored)
        {
            // Best-effort cleanup.
        }
    }

    private record DisplayPosition(@NotNull UUID worldId, int x, int y, int z) {}

    private final class GlowingFakeBlockElement extends BlockElement
    {
        private final @NotNull BlockData realBlock;
        private final @NotNull BlockData visualizedBlock;

        private GlowingFakeBlockElement(
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
            BlockDisplay display = spawnDisplay(player, world, getCoordinate(), visualizedBlock);
            if (display != null)
            {
                trackDisplay(player.getUniqueId(), world.getUID(), getCoordinate(), display);
            }
        }

        @Override
        protected void erase(@NotNull Player player, @NotNull World world)
        {
            player.sendBlockChange(getCoordinate().toLocation(world), realBlock);
            removeDisplay(player.getUniqueId(), world.getUID(), getCoordinate());
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!super.equals(other)) return false;
            if (getClass() != other.getClass()) return false;
            GlowingFakeBlockElement that = (GlowingFakeBlockElement) other;
            return realBlock.equals(that.realBlock) && visualizedBlock.equals(that.visualizedBlock);
        }

        @Override
        public int hashCode()
        {
            return super.hashCode();
        }
    }
}
