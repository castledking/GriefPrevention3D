package com.griefprevention.visualization;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Constants for types of boundaries.
 */
public enum VisualizationType implements VisualizationStyle
{

    /** Boundaries for a user claim. */
    CLAIM("claim", blockRenderer(
            () -> Material.GLOWSTONE.createBlockData(),
            () -> Material.GOLD_BLOCK.createBlockData())),
    /** Boundaries for an administrative claim. */
    ADMIN_CLAIM("admin_claim", blockRenderer(
            () -> Material.GLOWSTONE.createBlockData(),
            () -> Material.PUMPKIN.createBlockData())),
    /** Boundaries for a claim subdivision. */
    SUBDIVISION("subdivision", blockRenderer(
            () -> Material.IRON_BLOCK.createBlockData(),
            () -> Material.WHITE_WOOL.createBlockData())),
    /** Boundaries for a new claim area. */
    INITIALIZE_ZONE("initialize_zone", blockRenderer(
            () -> Material.DIAMOND_BLOCK.createBlockData(),
            () -> Material.DIAMOND_BLOCK.createBlockData())),
    /** Boundaries for a conflicting area. */
    CONFLICT_ZONE("conflict_zone", blockRenderer(
            () -> {
                BlockData fakeData = Material.REDSTONE_ORE.createBlockData();
                ((Lightable) fakeData).setLit(true);
                return fakeData;
            },
            () -> Material.NETHERRACK.createBlockData()));

    private final @NotNull NamespacedKey key;
    private final @NotNull BlockBoundaryRenderer blockRenderer;

    VisualizationType(@NotNull String key, @NotNull BlockBoundaryRenderer blockRenderer)
    {
        this.key = Objects.requireNonNull(NamespacedKey.fromString("griefprevention:" + key));
        this.blockRenderer = blockRenderer;
    }

    @Override
    public @NotNull NamespacedKey getKey()
    {
        return key;
    }

    @Override
    public @Nullable BlockBoundaryRenderer getBlockRenderer()
    {
        return blockRenderer;
    }

    public static @Nullable VisualizationType fromKey(@NotNull NamespacedKey key)
    {
        return Arrays.stream(values())
                .filter(type -> type.key.equals(key))
                .findFirst()
                .orElse(null);
    }

    private static @NotNull BlockBoundaryRenderer blockRenderer(
            @NotNull BlockDataSupplier cornerSupplier,
            @NotNull BlockDataSupplier sideSupplier)
    {
        return new BlockBoundaryRenderer()
        {
            @Override
            public @NotNull BlockData createCornerBlockData(@NotNull Boundary boundary)
            {
                return cornerSupplier.create();
            }

            @Override
            public @NotNull BlockData createSideBlockData(@NotNull Boundary boundary)
            {
                return sideSupplier.create();
            }
        };
    }

    @FunctionalInterface
    private interface BlockDataSupplier
    {
        @NotNull BlockData create();
    }

}
