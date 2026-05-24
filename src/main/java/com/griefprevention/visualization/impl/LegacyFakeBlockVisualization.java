package com.griefprevention.visualization.impl;

import com.griefprevention.compat.MaterialTagCompat;
import com.griefprevention.util.IntVector;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.compat.LegacyFakeBlockElement;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class LegacyFakeBlockVisualization extends FakeBlockVisualization
{
    // Materials resolved via valueOf() string lookup to avoid compile-time field references
    // to enum constants that don't exist in 1.8.8 (e.g. WHITE_WOOL was renamed from WOOL in 1.13).
    // On 1.13+ this class is never loaded (guarded by !hasBlockData()), so null fallback is safe.
    private static final @NotNull Material WOOL;
    private static final @NotNull Material LIT_REDSTONE_ORE;
    private static final @NotNull Material LIME_STAINED_CLAY;

    static
    {
        WOOL = resolveLegacyMaterial("WOOL");
        LIT_REDSTONE_ORE = resolveLegacyMaterial("GLOWING_REDSTONE_ORE");
        LIME_STAINED_CLAY = resolveLegacyMaterial("STAINED_CLAY");
    }

    private static @NotNull Material resolveLegacyMaterial(@NotNull String name)
    {
        try
        {
            return Material.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            // Fallback — should never happen since this class is only loaded on pre-1.13 servers
            // where the legacy name exists. Avoids any compile-time reference to modern-only
            // Material enum constants.
            return Material.STONE;
        }
    }

    public LegacyFakeBlockVisualization(@NotNull World world, @NotNull IntVector visualizeFrom, int height)
    {
        super(world, visualizeFrom, height);
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addCornerElements(@NotNull Boundary boundary)
    {
        return createLegacyElementAdder(
                cornerMaterialFor(boundary.type()),
                cornerDataFor(boundary.type()),
                boundary.type(),
                usesExactPlacement(boundary.type()));
    }

    @Override
    protected @NotNull Consumer<@NotNull IntVector> addSideElements(@NotNull Boundary boundary)
    {
        return createLegacyElementAdder(
                sideMaterialFor(boundary.type()),
                sideDataFor(boundary.type()),
                boundary.type(),
                usesExactPlacement(boundary.type()));
    }

    @Override
    protected void drawRestoreNature(@NotNull Player player, @NotNull Boundary boundary)
    {
        BoundingBox area = boundary.bounds();
        VisualizationType type = boundary.type();

        final int displayZoneRadius = 75;
        int baseX = this.visualizeFrom.x();
        int baseZ = this.visualizeFrom.z();
        BoundingBox displayZoneArea = new BoundingBox(
            new IntVector(baseX - displayZoneRadius, GriefPrevention.getWorldMinY(world), baseZ - displayZoneRadius),
            new IntVector(baseX + displayZoneRadius, GriefPrevention.getWorldMaxY(world), baseZ + displayZoneRadius)
        );
        BoundingBox displayZone = displayZoneArea.intersection(area);
        if (displayZone == null) return;

        Consumer<@NotNull IntVector> addSide = addSideElements(boundary);

        if (area.getLength() > 2)
        {
            addDisplayed(displayZone, new IntVector(area.getMinX() + 1, height, area.getMaxZ()), addSide);
            addDisplayed(displayZone, new IntVector(area.getMinX() + 1, height, area.getMinZ()), addSide);
            addDisplayed(displayZone, new IntVector(area.getMaxX() - 1, height, area.getMaxZ()), addSide);
            addDisplayed(displayZone, new IntVector(area.getMaxX() - 1, height, area.getMinZ()), addSide);
        }
        if (area.getWidth() > 2)
        {
            addDisplayed(displayZone, new IntVector(area.getMinX(), height, area.getMinZ() + 1), addSide);
            addDisplayed(displayZone, new IntVector(area.getMaxX(), height, area.getMinZ() + 1), addSide);
            addDisplayed(displayZone, new IntVector(area.getMinX(), height, area.getMaxZ() - 1), addSide);
            addDisplayed(displayZone, new IntVector(area.getMaxX(), height, area.getMaxZ() - 1), addSide);
        }

        Consumer<@NotNull IntVector> addCorner = addCornerElements(boundary);
        addDisplayed(displayZone, new IntVector(area.getMinX(), height, area.getMaxZ()), addCorner);
        addDisplayed(displayZone, new IntVector(area.getMaxX(), height, area.getMaxZ()), addCorner);
        addDisplayed(displayZone, new IntVector(area.getMinX(), height, area.getMinZ()), addCorner);
        addDisplayed(displayZone, new IntVector(area.getMaxX(), height, area.getMinZ()), addCorner);
    }

    @Override
    protected void onElementAdded(
            @NotNull IntVector location,
            @NotNull BlockData fakeData,
            @NotNull VisualizationType type)
    {
    }

    private @NotNull Consumer<@NotNull IntVector> createLegacyElementAdder(
            @NotNull Material fakeMat,
            byte fakeData,
            @NotNull VisualizationType type,
            boolean exactPlacement)
    {
        return exactPlacement ? addLegacyExactBlockElement(fakeMat, fakeData, type)
                              : addLegacyBlockElement(fakeMat, fakeData, type);
    }

    @SuppressWarnings("deprecation")
    private @NotNull Consumer<@NotNull IntVector> addLegacyBlockElement(
            @NotNull Material fakeMat,
            byte fakeData,
            @NotNull VisualizationType type)
    {
        return vector -> {
            Block visibleLocation = getVisibleLocation(vector);
            IntVector location = new IntVector(visibleLocation);
            elements.add(new LegacyFakeBlockElement(
                    location,
                    visibleLocation.getType(),
                    visibleLocation.getData(),
                    fakeMat,
                    fakeData));
            onElementAdded(location, null, type);
        };
    }

    @SuppressWarnings("deprecation")
    private @NotNull Consumer<@NotNull IntVector> addLegacyExactBlockElement(
            @NotNull Material fakeMat,
            byte fakeData,
            @NotNull VisualizationType type)
    {
        return vector -> {
            Block exactLocation = vector.toBlock(world);
            IntVector location = new IntVector(exactLocation);
            elements.add(new LegacyFakeBlockElement(
                    location,
                    exactLocation.getType(),
                    exactLocation.getData(),
                    fakeMat,
                    fakeData));
            onElementAdded(location, null, type);
        };
    }

    private static boolean usesExactPlacement(@NotNull VisualizationType type)
    {
        return type == VisualizationType.SUBDIVISION_3D
                || type == VisualizationType.CONFLICT_ZONE_3D
                || type == VisualizationType.ADMIN_CLAIM_3D
                || type == VisualizationType.INITIALIZE_ZONE_3D;
    }

    private static @NotNull Material cornerMaterialFor(@NotNull VisualizationType type)
    {
        switch (type)
        {
            case CLAIM:
            case ADMIN_CLAIM:
            case ADMIN_CLAIM_3D:
                return Material.GLOWSTONE;
            case SUBDIVISION:
            case SUBDIVISION_3D:
                return Material.IRON_BLOCK;
            case INITIALIZE_ZONE:
            case INITIALIZE_ZONE_3D:
                return Material.DIAMOND_BLOCK;
            case CONFLICT_ZONE:
                return LIT_REDSTONE_ORE;
            case CONFLICT_ZONE_3D:
                return Material.REDSTONE_ORE;
            case RESTORE_NATURE:
                return LIME_STAINED_CLAY;
            default:
                return Material.GLOWSTONE;
        }
    }

    private static byte cornerDataFor(@NotNull VisualizationType type)
    {
        switch (type)
        {
            case RESTORE_NATURE:
                return 5;
            default:
                return 0;
        }
    }

    private static @NotNull Material sideMaterialFor(@NotNull VisualizationType type)
    {
        switch (type)
        {
            case CLAIM:
                return Material.GOLD_BLOCK;
            case ADMIN_CLAIM:
            case ADMIN_CLAIM_3D:
                return Material.PUMPKIN;
            case SUBDIVISION:
            case SUBDIVISION_3D:
                return WOOL;
            case INITIALIZE_ZONE:
            case INITIALIZE_ZONE_3D:
                return Material.DIAMOND_BLOCK;
            case CONFLICT_ZONE:
            case CONFLICT_ZONE_3D:
                return Material.NETHERRACK;
            case RESTORE_NATURE:
                return Material.EMERALD_BLOCK;
            default:
                return Material.GOLD_BLOCK;
        }
    }

    private static byte sideDataFor(@NotNull VisualizationType type)
    {
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected boolean isTransparent(@NotNull Block block)
    {
        Material blockMaterial = block.getType();

        switch (blockMaterial)
        {
            case WATER:
                return waterTransparent;
            case SNOW:
                return false;
            default:
                break;
        }

        if (
            blockMaterial == Material.AIR ||
            MaterialTagCompat.isTagged("FENCES", blockMaterial) ||
            MaterialTagCompat.isTagged("FENCE_GATES", blockMaterial) ||
            MaterialTagCompat.isTagged("SIGNS", blockMaterial) ||
            MaterialTagCompat.isTagged("WALLS", blockMaterial) ||
            MaterialTagCompat.isTagged("WALL_SIGNS", blockMaterial)
        ) return true;

        boolean transparent = block.getType().isTransparent();
        return transparent;
    }
}
