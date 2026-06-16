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
        /** Paper and Paper forks (Purpur, Pufferfish, etc.) */
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

        Platform platform = getPlatform();
        String name;
        switch (platform) {
            case PAPER:
                name = "Paper";
                break;
            case PURPUR:
                name = "Purpur";
                break;
            case FOLIA:
                name = "Folia";
                break;
            default:
                name = "Spigot";
                break;
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