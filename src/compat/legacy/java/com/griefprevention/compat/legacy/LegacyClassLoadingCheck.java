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
            if (className.endsWith("#construct")) {
                construct(className.substring(0, className.length() - "#construct".length()), classLoader);
            } else {
                Class.forName(className, true, classLoader);
            }
        }
    }

    private static void construct(String className, ClassLoader classLoader) throws Exception {
        Class<?> type = Class.forName(className, true, classLoader);
        if ("me.ryanhamshire.GriefPrevention.PlayerEventHandler".equals(className)) {
            Class<?> dataStoreType = Class.forName("me.ryanhamshire.GriefPrevention.DataStore", false, classLoader);
            Class<?> pluginType = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention", false, classLoader);
            Constructor<?> constructor = type.getDeclaredConstructor(dataStoreType, pluginType, boolean.class);
            constructor.setAccessible(true);
            constructor.newInstance(null, null, false);
        }
    }
}
