/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Represents a snapshot of a block's state for restore nature operations.
 * This is used to capture block data on the main thread and process it asynchronously.
 */
public class BlockSnapshot {
    public Location location;
    public Material material;
    public Object blockData; // Changed from BlockData to Object for legacy compatibility
    public boolean protectedFromRestore;

    public BlockSnapshot(Location location, Material material, Object blockData) {
        this.location = location;
        this.material = material;
        this.blockData = blockData;
    }

    public BlockSnapshot(Block block) {
        this.location = block.getLocation();
        this.material = block.getType();
        this.blockData = getBlockData(block);
        this.protectedFromRestore = isQuickShopSign(block);
    }

    /**
     * Apply this snapshot to the world.
     * Must be called from the main thread or appropriate region thread.
     */
    public void apply() {
        Block block = location.getBlock();
        setBlockData(block, blockData);
    }

    private static Object getBlockData(Block block) {
        try {
            return Block.class.getMethod("getBlockData").invoke(block);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private void setBlockData(Block block, Object blockData) {
        if (blockData == null) {
            return;
        }
        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData", false, BlockSnapshot.class.getClassLoader());
            Block.class.getMethod("setBlockData", blockDataClass).invoke(block, blockData);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Legacy servers don't support BlockData, just set material
            block.setType(this.material, false);
        }
    }

    private boolean isQuickShopSign(Block block) {
        if (!material.name().endsWith("_SIGN")) {
            return false;
        }

        // The persistent data API is 1.14+. Naming those types here would make this class fail
        // verification on older servers, so the lookup is done entirely through reflection.
        return CompatUtil.hasPersistentDataKey(block.getState(), "shopsign", "quickshop");
    }
}
