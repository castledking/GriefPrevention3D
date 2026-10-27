package com.griefprevention.compat.legacy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Small executable used by Gradle to catch hard references to APIs missing from legacy Bukkit.
 */
public final class LegacyClassLoadingCheck {

    private LegacyClassLoadingCheck() {
    }

    public static void main(String[] classNames) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String className : classNames) {
            try {
                if (className.endsWith("#construct")) {
                    construct(className.substring(0, className.length() - "#construct".length()), classLoader);
                } else if (className.endsWith("#restore-nature-biome")) {
                    restoreNatureBiome(className.substring(0, className.length() - "#restore-nature-biome".length()), classLoader);
                } else if (className.endsWith("#collision-boxes")) {
                    collisionBoxes(className.substring(0, className.length() - "#collision-boxes".length()), classLoader);
                } else {
                    Class.forName(className, true, classLoader);
                }
            } catch (Throwable t) {
                System.err.println("Failed to load: " + className);
                throw t;
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void collisionBoxes(String className, ClassLoader classLoader) throws Exception {
        Class<?> blockType = Class.forName("org.bukkit.block.Block", false, classLoader);
        Class<?> materialType = Class.forName("org.bukkit.Material", true, classLoader);
        Object stone = Enum.valueOf((Class<? extends Enum>) materialType.asSubclass(Enum.class), "STONE");
        Object block = Proxy.newProxyInstance(classLoader, new Class<?>[] { blockType }, (proxy, method, args) -> {
            if (method.getName().equals("getType")) return stone;
            if (method.getName().equals("getX") || method.getName().equals("getZ")) return 0;
            if (method.getName().equals("getY")) return 63;
            throw new UnsupportedOperationException(method.getName());
        });
        Class<?> helper = Class.forName(className, true, classLoader);
        List<?> boxes = (List<?>) helper.getMethod("boxes", blockType).invoke(null, block);
        if (boxes.size() != 1) throw new AssertionError("Expected a solid legacy collision box, got " + boxes.size());
        if ((Boolean) helper.getMethod("isClimbable", blockType).invoke(null, block))
            throw new AssertionError("Stone must not be treated as a legacy climbable");
        for (String name : new String[] { "LADDER", "VINE" }) {
            Object material = Enum.valueOf((Class<? extends Enum>) materialType.asSubclass(Enum.class), name);
            Object climbable = Proxy.newProxyInstance(classLoader, new Class<?>[] { blockType }, (proxy, method, args) -> {
                if (method.getName().equals("getType")) return material;
                throw new UnsupportedOperationException(method.getName());
            });
            if (!(Boolean) helper.getMethod("isClimbable", blockType).invoke(null, climbable))
                throw new AssertionError("Expected legacy climbable: " + name);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void restoreNatureBiome(String className, ClassLoader classLoader) throws Exception {
        Class<?> taskType = Class.forName(className, true, classLoader);
        Class<?> biomeType = Class.forName("org.bukkit.block.Biome", true, classLoader);
        Method getBiomeName = taskType.getDeclaredMethod("getBiomeName", biomeType);
        getBiomeName.setAccessible(true);
        Object desert = Enum.valueOf((Class<? extends Enum>) biomeType.asSubclass(Enum.class), "DESERT");
        String biomeName = (String) getBiomeName.invoke(null, desert);
        if (!biomeName.contains("DESERT")) {
            throw new AssertionError("Expected legacy DESERT biome name, got: " + biomeName);
        }
    }

    private static void construct(String className, ClassLoader classLoader) throws Exception {
        Class<?> type = Class.forName(className, true, classLoader);
        if ("me.ryanhamshire.GriefPrevention.PlayerEventHandler".equals(className)) {
            Class<?> dataStoreType = Class.forName("me.ryanhamshire.GriefPrevention.DataStore", false, classLoader);
            Class<?> pluginType = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention", false, classLoader);
            Constructor<?> constructor = type.getDeclaredConstructor(dataStoreType, pluginType);
            constructor.setAccessible(true);
            try {
                constructor.newInstance(null, null);
            } catch (java.lang.reflect.InvocationTargetException invocationException) {
                Throwable cause = invocationException.getCause();
                // NPEs from passing null args are acceptable here; we only care about linkage errors
                // and missing API references that would also surface with real arguments.
                if (cause instanceof NullPointerException) {
                    return;
                }
                if (cause instanceof LinkageError || cause instanceof ClassNotFoundException
                        || cause instanceof NoSuchFieldError || cause instanceof NoSuchMethodError) {
                    throw invocationException;
                }
                // Any other RuntimeException from constructor body is also acceptable (test data is null).
                if (cause instanceof RuntimeException) {
                    return;
                }
                throw invocationException;
            }
        }
    }
}
