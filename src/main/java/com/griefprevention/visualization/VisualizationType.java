package com.griefprevention.visualization;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Constants for types of boundaries.
 */
public enum VisualizationType
{

    /** Boundaries for a user claim. */
    CLAIM,
    /** Boundaries for an administrative claim. */
    ADMIN_CLAIM,
    /** Boundaries for a 3D administrative claim with vertical limits. */
    ADMIN_CLAIM_3D,
    /** Boundaries for a claim subdivision. */
    SUBDIVISION,
    /** Boundaries for a 3D claim subdivision with vertical limits. */
    SUBDIVISION_3D,
    /** Boundaries for a new claim area. */
    INITIALIZE_ZONE,
    /** Boundaries for a new 3D claim area (exact Y placement). */
    INITIALIZE_ZONE_3D,
    /** Boundaries for a conflicting area. */
    CONFLICT_ZONE,
    /** Boundaries for a conflicting 3D subdivision area. */
    CONFLICT_ZONE_3D,
    /** Boundaries showing a restored nature area. */
    RESTORE_NATURE;

    private BlockBoundaryRenderer blockRenderer;

    /**
     * Get the block renderer for this visualization type.
     * Lazy initialization to handle 1.8.8 compatibility.
     */
    public @NotNull BlockBoundaryRenderer getBlockRenderer()
    {
        if (blockRenderer == null) {
            blockRenderer = createBlockRenderer();
        }
        return blockRenderer;
    }

    private @NotNull BlockBoundaryRenderer createBlockRenderer()
    {
        boolean hasBlockData = hasBlockData();

        switch (this) {
            case CLAIM:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.GLOWSTONE),
                        () -> createBlockData(hasBlockData, Material.GOLD_BLOCK));
            case ADMIN_CLAIM:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.GLOWSTONE),
                        () -> createBlockData(hasBlockData, Material.PUMPKIN));
            case ADMIN_CLAIM_3D:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.GLOWSTONE),
                        () -> createBlockData(hasBlockData, Material.PUMPKIN));
            case SUBDIVISION:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.IRON_BLOCK),
                        () -> createBlockData(hasBlockData, Material.WHITE_WOOL));
            case SUBDIVISION_3D:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.IRON_BLOCK),
                        () -> createBlockData(hasBlockData, Material.WHITE_WOOL));
            case INITIALIZE_ZONE:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.DIAMOND_BLOCK),
                        () -> createBlockData(hasBlockData, Material.DIAMOND_BLOCK));
            case INITIALIZE_ZONE_3D:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.DIAMOND_BLOCK),
                        () -> createBlockData(hasBlockData, Material.DIAMOND_BLOCK));
            case CONFLICT_ZONE:
                return blockRenderer(
                        () -> {
                            BlockData fakeData = createBlockData(hasBlockData, Material.REDSTONE_ORE);
                            if (fakeData instanceof Lightable) {
                                ((Lightable) fakeData).setLit(true);
                            }
                            return fakeData;
                        },
                        () -> createBlockData(hasBlockData, Material.NETHERRACK));
            case CONFLICT_ZONE_3D:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.REDSTONE_ORE),
                        () -> createBlockData(hasBlockData, Material.NETHERRACK));
            case RESTORE_NATURE:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.LIME_GLAZED_TERRACOTTA),
                        () -> createBlockData(hasBlockData, Material.EMERALD_BLOCK));
            default:
                return blockRenderer(
                        () -> createBlockData(hasBlockData, Material.GLOWSTONE),
                        () -> createBlockData(hasBlockData, Material.GOLD_BLOCK));
        }
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

    /**
     * Check if BlockData API is available (1.13+).
     */
    private static boolean hasBlockData() {
        try {
            Class.forName("org.bukkit.block.data.BlockData");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Safely create BlockData for a material. Returns null if BlockData doesn't exist (1.8.8).
     */
    private static @Nullable BlockData createBlockData(boolean hasBlockData, Material material) {
        if (!hasBlockData) {
            return null;
        }
        try {
            return material.createBlockData();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return null;
        }
    }
}
