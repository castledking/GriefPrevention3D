package com.griefprevention.addon.gp3d;

import com.griefprevention.visualization.BlockBoundaryRenderer;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.VisualizationStyle;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class StackedSubdivisionStyle implements VisualizationStyle
{
    static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("gp3daddon:stacked_subdivision"));

    private static final BlockBoundaryRenderer RENDERER = new BlockBoundaryRenderer()
    {
        @Override
        public @NotNull BlockData createCornerBlockData(@NotNull Boundary boundary)
        {
            return Material.IRON_BLOCK.createBlockData();
        }

        @Override
        public @NotNull BlockData createSideBlockData(@NotNull Boundary boundary)
        {
            return Material.WHITE_WOOL.createBlockData();
        }
    };

    @Override
    public @NotNull NamespacedKey getKey()
    {
        return KEY;
    }

    @Override
    public @Nullable BlockBoundaryRenderer getBlockRenderer()
    {
        return RENDERER;
    }
}
