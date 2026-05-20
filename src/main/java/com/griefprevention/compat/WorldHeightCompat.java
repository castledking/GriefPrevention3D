package com.griefprevention.compat;

import org.bukkit.World;

/**
 * Version-sensitive world height access.
 */
public interface WorldHeightCompat {

    int minHeight(World world);

    int maxHeight(World world);
}
