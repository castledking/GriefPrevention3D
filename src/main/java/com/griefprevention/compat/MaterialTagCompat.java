package com.griefprevention.compat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-safe access to Bukkit material tags, which do not exist on older Bukkit APIs.
 */
public final class MaterialTagCompat {

    private static final String BUKKIT_TAG_CLASS = "org.bukkit.Tag";
    private static final Map<String, Set<Material>> TAG_VALUES = new ConcurrentHashMap<>();

    private MaterialTagCompat() {
    }

    public static @NotNull Set<Material> values(@NotNull String tagName) {
        String normalized = tagName.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        if (!isBukkitServerAvailable()) {
            return Collections.emptySet();
        }

        return TAG_VALUES.computeIfAbsent(normalized, MaterialTagCompat::resolveValues);
    }

    public static boolean isTagged(@NotNull String tagName, @NotNull Material material) {
        return values(tagName).contains(material);
    }

    private static @NotNull Set<Material> resolveValues(@NotNull String tagName) {
        try {
            Class<?> tagClass = Class.forName(BUKKIT_TAG_CLASS, false, MaterialTagCompat.class.getClassLoader());
            Object tag = tagClass.getField(tagName).get(null);
            Object values = tagClass.getMethod("getValues").invoke(tag);
            if (!(values instanceof Iterable<?>)) {
                return Collections.emptySet();
            }

            Set<Material> materials = new LinkedHashSet<>();
            for (Object value : (Iterable<?>) values) {
                if (value instanceof Material) {
                    materials.add((Material) value);
                }
            }
            return Collections.unmodifiableSet(materials);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return Collections.emptySet();
        }
    }

    private static boolean isBukkitServerAvailable() {
        try {
            return Bukkit.getServer() != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }
}
