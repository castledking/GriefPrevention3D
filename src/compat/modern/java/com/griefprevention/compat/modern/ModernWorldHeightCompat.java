package com.griefprevention.compat.modern;

import com.griefprevention.compat.WorldHeightCompat;
import org.bukkit.World;

/**
 * World height access for modern Bukkit APIs with configurable min/max build height.
 */
public final class ModernWorldHeightCompat implements WorldHeightCompat {

    @Override
    public int minHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public int maxHeight(World world) {
        return world.getMaxHeight();
    }
}
