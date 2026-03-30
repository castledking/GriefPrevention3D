package com.griefprevention.visualization;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Constants for types of boundaries.
 */
public enum VisualizationType
{

    /** Boundaries for a user claim. */
    CLAIM(blockRenderer(
            () -> Material.GLOWSTONE.createBlockData(),
            () -> Material.GOLD_BLOCK.createBlockData())),
    /** Boundaries for an administrative claim. */
    ADMIN_CLAIM(blockRenderer(
            () -> Material.GLOWSTONE.createBlockData(),
            () -> Material.PUMPKIN.createBlockData())),
    /** Boundaries for a claim subdivision. */
    SUBDIVISION(blockRenderer(
            () -> Material.IRON_BLOCK.createBlockData(),
            () -> Material.WHITE_WOOL.createBlockData())),
    /** Boundaries for a 3D claim subdivision with vertical limits. */
    SUBDIVISION_3D(blockRenderer(
            () -> Material.IRON_BLOCK.createBlockData(),
            () -> Material.WHITE_WOOL.createBlockData())),
    /** Boundaries for a new claim area. */
    INITIALIZE_ZONE(blockRenderer(
            () -> Material.DIAMOND_BLOCK.createBlockData(),
            () -> Material.DIAMOND_BLOCK.createBlockData())),
    /** Boundaries for a conflicting area. */
    CONFLICT_ZONE(blockRenderer(
            () -> {
                BlockData fakeData = Material.REDSTONE_ORE.createBlockData();
                ((Lightable) fakeData).setLit(true);
                return fakeData;
            },
            () -> Material.NETHERRACK.createBlockData())),
    /** Boundaries for a conflicting 3D subdivision area. */
    CONFLICT_ZONE_3D(blockRenderer(
            () -> Material.REDSTONE_ORE.createBlockData(),
            () -> Material.NETHERRACK.createBlockData())),
    /** Boundaries showing a restored nature area. */
    RESTORE_NATURE(blockRenderer(
            () -> Material.LIME_GLAZED_TERRACOTTA.createBlockData(),
            () -> Material.EMERALD_BLOCK.createBlockData()));

    private final @NotNull BlockBoundaryRenderer blockRenderer;

    VisualizationType(@NotNull BlockBoundaryRenderer blockRenderer)
    {
        this.blockRenderer = blockRenderer;
    }

    public @NotNull BlockBoundaryRenderer getBlockRenderer()
    {
        return blockRenderer;
    }

    private static @NotNull BlockBoundaryRenderer blockRenderer(
            @NotNull Supplier<BlockData> cornerSupplier,
            @NotNull Supplier<BlockData> sideSupplier)
    {
        return new BlockBoundaryRenderer()
        {
            @Override
            public @NotNull BlockData createCornerBlockData(@NotNull Boundary boundary)
            {
                return cornerSupplier.get();
            }

            @Override
            public @NotNull BlockData createSideBlockData(@NotNull Boundary boundary)
            {
                return sideSupplier.get();
            }
        };
    }
}
