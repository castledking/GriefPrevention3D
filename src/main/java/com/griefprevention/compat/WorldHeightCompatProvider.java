package com.griefprevention.compat;

import org.bukkit.World;

/**
 * Selects world-height behavior without hard-linking main sources to version-specific implementations.
 */
public final class WorldHeightCompatProvider {

    private static final String MODERN_CLASS = "com.griefprevention.compat.modern.ModernWorldHeightCompat";
    private static final String LEGACY_CLASS = "com.griefprevention.compat.legacy.LegacyWorldHeightCompat";

    private static volatile WorldHeightCompat current;

    private WorldHeightCompatProvider() {
    }

    public static WorldHeightCompat current() {
        WorldHeightCompat selected = current;
        if (selected == null) {
            selected = select(hasWorldMinHeight());
            current = selected;
        }
        return selected;
    }

    static WorldHeightCompat select(boolean supportsMinHeight) {
        WorldHeightCompat packaged = instantiate(supportsMinHeight ? MODERN_CLASS : LEGACY_CLASS);
        if (packaged != null) {
            return packaged;
        }

        return supportsMinHeight ? new DirectModernWorldHeightCompat() : new DirectLegacyWorldHeightCompat();
    }

    private static boolean hasWorldMinHeight() {
        try {
            World.class.getMethod("getMinHeight");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static WorldHeightCompat instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getDeclaredConstructor().newInstance();
            return instance instanceof WorldHeightCompat ? (WorldHeightCompat) instance : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static final class DirectModernWorldHeightCompat implements WorldHeightCompat {

        @Override
        public int minHeight(World world) {
            return world.getMinHeight();
        }

        @Override
        public int maxHeight(World world) {
            return world.getMaxHeight();
        }
    }

    private static final class DirectLegacyWorldHeightCompat implements WorldHeightCompat {

        @Override
        public int minHeight(World world) {
            return 0;
        }

        @Override
        public int maxHeight(World world) {
            return world.getMaxHeight();
        }
    }
}
