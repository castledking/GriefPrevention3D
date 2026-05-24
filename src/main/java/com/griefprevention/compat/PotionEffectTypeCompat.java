package com.griefprevention.compat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflection-based access to PotionEffectType constants for legacy compatibility.
 */
public final class PotionEffectTypeCompat {

    private static volatile Set<Object> GRIEF_EFFECTS;

    private PotionEffectTypeCompat() {
    }

    /**
     * Gets the set of potion effect types that can be used for griefing.
     * Uses reflection to avoid hard references to modern constants.
     */
    public static @NotNull Set<Object> getGriefEffects() {
        Set<Object> cached = GRIEF_EFFECTS;
        if (cached != null) {
            return cached;
        }

        Set<Object> effects = new HashSet<>();

        // Try to add modern effect types by name
        addEffectByName(effects, "INSTANT_DAMAGE");
        addEffectByName(effects, "POISON");
        addEffectByName(effects, "WITHER");
        addEffectByName(effects, "JUMP_BOOST");
        addEffectByName(effects, "LEVITATION");

        // Legacy fallback names
        addEffectByName(effects, "HARM");  // Old name for INSTANT_DAMAGE
        addEffectByName(effects, "JUMP");  // Old name for JUMP_BOOST

        GRIEF_EFFECTS = effects;
        return effects;
    }

    private static void addEffectByName(@NotNull Set<Object> effects, @NotNull String name) {
        try {
            Class<?> potionEffectTypeClass = Class.forName("org.bukkit.potion.PotionEffectType", false, PotionEffectTypeCompat.class.getClassLoader());
            Field field = potionEffectTypeClass.getField(name);
            Object type = field.get(null);
            if (type != null) {
                effects.add(type);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Effect type doesn't exist in this version
        }
    }

    /**
     * Gets a PotionEffectType by name, returning null if it doesn't exist.
     */
    public static @Nullable Object getByName(@NotNull String name) {
        try {
            Class<?> potionEffectTypeClass = Class.forName("org.bukkit.potion.PotionEffectType", false, PotionEffectTypeCompat.class.getClassLoader());
            Field field = potionEffectTypeClass.getField(name);
            return field.get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
