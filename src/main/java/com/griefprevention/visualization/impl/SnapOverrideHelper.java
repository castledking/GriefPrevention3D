package com.griefprevention.visualization.impl;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.AmethystCluster;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.BigDripleaf;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.SmallDripleaf;
import org.bukkit.block.data.type.Campfire;
import org.jetbrains.annotations.NotNull;

final class SnapOverrideHelper
{
    private SnapOverrideHelper()
    {
    }

    enum SnapOverride
    {
        SELF,
        ABOVE,
        TWO_ABOVE,
        COLUMN_SURFACE,
        COLUMN_SEABED
    }

    static SnapOverride resolve(@NotNull Block block, boolean submerged)
    {
        BlockData data = block.getBlockData();
        Material material = block.getType();

        if (data instanceof Door door)
        {
            return door.getHalf() == Door.Half.BOTTOM ? SnapOverride.ABOVE : SnapOverride.SELF;
        }

        if (submerged && block.getType() == Material.TALL_SEAGRASS && data instanceof Bisected bisected) {
            return bisected.getHalf() == Bisected.Half.BOTTOM ? SnapOverride.ABOVE : SnapOverride.SELF;
        }

        if (data instanceof BigDripleaf)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof SmallDripleaf smallDripleaf)
        {
            return smallDripleaf.getHalf() == Bisected.Half.BOTTOM ? SnapOverride.ABOVE : SnapOverride.SELF;
        }

        if (data instanceof Bed)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof Bell)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof Lantern)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof Campfire)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof EndPortalFrame)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof PointedDripstone)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof Candle)
        {
            return SnapOverride.SELF;
        }

        if (data instanceof AmethystCluster)
        {
            return SnapOverride.SELF;
        }

        if (submerged && (material == Material.SEAGRASS || material == Material.SEA_PICKLE || material == Material.KELP || material == Material.KELP_PLANT)) {
            return SnapOverride.SELF;
        }

        if (material.name().contains("GLASS_PANE")
                || material.name().contains("ANVIL")
                || material.name().contains("CHEST"))
        {
            return SnapOverride.SELF;
        }

        if (Tag.TRAPDOORS.isTagged(material) || Tag.PRESSURE_PLATES.isTagged(material))
        {
            return SnapOverride.SELF;
        }

        if (material == Material.IRON_BARS)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.SCAFFOLDING)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.DECORATED_POT)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.SWEET_BERRY_BUSH)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.CHAIN)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.COMPOSTER)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.CAULDRON)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.CAKE)
        {
            return SnapOverride.SELF;
        }

        if (material == Material.MOSS_CARPET || material == Material.PALE_MOSS_CARPET)
        {
            return SnapOverride.SELF;
        }

        if (Tag.BANNERS.isTagged(material))
        {
            return SnapOverride.SELF;
        }

        if (material == Material.LAVA)
        {
            return SnapOverride.COLUMN_SURFACE;
        }

        if (material == Material.TURTLE_EGG) {
            return SnapOverride.SELF;
        }

        if (material == Material.SCULK_SENSOR) {
            return SnapOverride.SELF;
        }

        if (material == Material.CALIBRATED_SCULK_SENSOR) {
            return SnapOverride.SELF;
        }

        if (material == Material.CACTUS) {
            return SnapOverride.SELF;
        }

        if (material == Material.BAMBOO) {
            return SnapOverride.SELF;
        }

        if (material == Material.DAYLIGHT_DETECTOR) {
            return SnapOverride.SELF;
        }

        if (material == Material.GRINDSTONE) {
            return SnapOverride.SELF;
        }

        if (material == Material.LECTERN) {
            return SnapOverride.SELF;
        }

        if (material == Material.ENCHANTING_TABLE) {
            return SnapOverride.SELF;
        }
        
        if (material == Material.STONECUTTER) {
            return SnapOverride.SELF;
        }

        return null;
    }

    static boolean isLiquidLike(@NotNull Block block, boolean submerged)
    {
        if (block.isLiquid())
        {
            return true;
        }

        Material material = block.getType();

        if (submerged && (material == Material.BUBBLE_COLUMN
                || material == Material.KELP
                || material == Material.KELP_PLANT
                || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS
                || material == Material.SEA_PICKLE))
        {
            return true;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged())
        {
            return true;
        }

        return false;
    }
}
