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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * Represents a snapshot of a block's state for restore nature operations.
 * This is used to capture block data on the main thread and process it asynchronously.
 */
public class BlockSnapshot {
    public Location location;
    public Material material;
    public BlockData blockData;
    public boolean protectedFromRestore;

    public BlockSnapshot(Location location, Material material, BlockData blockData) {
        this.location = location;
        this.material = material;
        this.blockData = blockData;
    }

    public BlockSnapshot(Block block) {
        this.location = block.getLocation();
        this.material = block.getType();
        this.blockData = block.getBlockData().clone();
        this.protectedFromRestore = isQuickShopSign(block);
    }

    /**
     * Apply this snapshot to the world.
     * Must be called from the main thread or appropriate region thread.
     */
    public void apply() {
        Block block = location.getBlock();
        block.setBlockData(blockData, false);
    }

    private boolean isQuickShopSign(Block block) {
        if (!material.name().endsWith("_SIGN")) {
            return false;
        }

        if (!(block.getState() instanceof TileState state)) {
            return false;
        }

        return hasQuickShopSignKey(state);
    }

    private boolean hasQuickShopSignKey(PersistentDataHolder holder) {
        PersistentDataContainer container = holder.getPersistentDataContainer();
        return container.getKeys().stream()
                .anyMatch(key -> key.getKey().equals("shopsign")
                        && (key.getNamespace().equalsIgnoreCase("quickshop")
                        || key.getNamespace().toLowerCase(java.util.Locale.ROOT).contains("quickshop")));
    }
}
