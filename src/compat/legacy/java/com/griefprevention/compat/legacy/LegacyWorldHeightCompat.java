package com.griefprevention.compat.legacy;

import com.griefprevention.compat.WorldHeightCompat;
import org.bukkit.World;

/**
 * World height access for old Bukkit APIs where worlds always start at Y=0.
 */
public final class LegacyWorldHeightCompat implements WorldHeightCompat {

    @Override
    public int minHeight(World world) {
        return 0;
    }

    @Override
    public int maxHeight(World world) {
        return world.getMaxHeight();
    }
}
