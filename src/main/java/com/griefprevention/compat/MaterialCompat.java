package com.griefprevention.compat;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime-safe material lookup for Bukkit versions with different enum constants.
 */
public final class MaterialCompat {

    private MaterialCompat() {
    }

    public static @Nullable Material get(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String normalized = name.trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(normalized);
        return material != null ? material : Material.getMaterial(normalized);
    }

    private static @Nullable Material PISTON_CACHE;
    private static @Nullable Material STICKY_PISTON_CACHE;

    public static @NotNull Material PISTON() {
        if (PISTON_CACHE == null) {
            PISTON_CACHE = get("PISTON_BASE");
            if (PISTON_CACHE == null) PISTON_CACHE = get("PISTON");
        }
        return PISTON_CACHE;
    }

    public static @NotNull Material STICKY_PISTON() {
        if (STICKY_PISTON_CACHE == null) {
            STICKY_PISTON_CACHE = get("PISTON_STICKY_BASE");
            if (STICKY_PISTON_CACHE == null) STICKY_PISTON_CACHE = get("STICKY_PISTON");
        }
        return STICKY_PISTON_CACHE;
    }

    public static @NotNull Set<Material> availableSet(@NotNull String... names) {
        Set<Material> materials = new LinkedHashSet<>();
        for (String name : names) {
            Material material = get(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return Collections.unmodifiableSet(materials);
    }
}
