package com.griefprevention.compat.legacy;

import java.lang.reflect.Constructor;

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
                } else {
                    Class.forName(className, true, classLoader);
                }
            } catch (Throwable t) {
                System.err.println("Failed to load: " + className);
                throw t;
            }
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
