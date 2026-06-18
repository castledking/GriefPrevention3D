package com.griefprevention.platform;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Central utility for detecting server platform at runtime.
 * <p>
 * Detection results are cached to avoid repeated reflection calls.
 *
 * @see PlatformListener for adding platform-specific listeners
 */
public final class PlatformDetection
{

    /**
     * Server platforms that the plugin can detect and adapt to.
     */
    public enum Platform
    {
        /** Canvas (Paper fork) */
        CANVAS,
        /** Paper (and generic Paper forks like Pufferfish) */
        PAPER,
        /** Spigot and Spigot-based servers without Paper API */
        SPIGOT,
        /** Purpur (Paper fork) */
        PURPUR,
        /** Folia */
        FOLIA
    }

    private static Platform detectedPlatform = null;

    private PlatformDetection()
    {
    }

    /**
     * Detects and returns the server platform.
     * Result is cached after first detection.
     *
     * @return the detected platform
     */
    public static @NotNull Platform getPlatform()
    {
        if (detectedPlatform == null)
        {
            detectedPlatform = detectPlatform();
        }
        return detectedPlatform;
    }

    private static @NotNull Platform detectPlatform()
    {
        try
        {
            String serverName = Bukkit.getName();

            // Canvas (Paper fork — check by Bukkit name before Paper class check)
            if ("Canvas".equalsIgnoreCase(serverName))
            {
                return Platform.CANVAS;
            }
        }
        catch (NullPointerException ignored)
        {
            // Bukkit not initialized (e.g. in test environment) — fall through to class-based checks
        }
        catch (NoSuchMethodError ignored)
        {
            // Very old server versions may not have Bukkit.getName()
        }

        if (classExists("com.destroystokyo.paper.PaperConfig")
                || classExists("io.papermc.paper.configuration.Configuration"))
        {
            return Platform.PAPER;
        }
        if (classExists("net.pl3x.purpur.PurpurConfig") || classExists("org.purpurmc.purpur.PurpurConfig")) {
            return Platform.PURPUR;
        }
        if (classExists("io.papermc.folia.FoliaConfig") || classExists("io.folia.Folia")) {
            return Platform.FOLIA;
        }
        return Platform.SPIGOT;
    }

    /**
     * Returns a concise server implementation + Minecraft version string, e.g. "Paper 1.21.11".
     */
    public static @NotNull String getServerVersion()
    {
        String mcVersion = Bukkit.getBukkitVersion();

        // Try to extract the numeric minecraft version like 1.21.11 or 1.8.8
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(mcVersion);
        String version = m.find() ? m.group(1) : mcVersion;

        String name;
        try
        {
            name = Bukkit.getName();
            // Spigot returns "CraftBukkit" — normalize to "Spigot"
            if ("CraftBukkit".equals(name))
            {
                name = "Spigot";
            }
        }
        catch (NullPointerException | NoSuchMethodError ignored)
        {
            // Bukkit not initialized (e.g. test environment) — fall back to platform enum
            name = getPlatform().name();
            name = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }
        return name + " " + version;
    }

    /**
     * Checks if a class exists on the classpath.
     * <p>
     * Useful for checking if platform-specific APIs are available
     * before attempting to use them.
     *
     * @param className the fully qualified class name
     * @return true if the class exists
     */
    public static boolean classExists(String className)
    {
        try
        {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

}