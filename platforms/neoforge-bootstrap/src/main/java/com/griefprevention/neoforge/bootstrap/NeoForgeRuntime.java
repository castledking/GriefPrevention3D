package com.griefprevention.neoforge.bootstrap;

/**
 * The running Minecraft release, as the bootstrap detected it, for the adapters it starts.
 *
 * <p>Adapters ask here instead of calling FML themselves: {@code FMLLoader}'s version API moved
 * between the NeoForge releases one adapter serves, and the bootstrap already reads both shapes.
 */
public final class NeoForgeRuntime
{
    private NeoForgeRuntime()
    {
    }

    /** @return the running Minecraft version, such as {@code 1.21.4} or {@code 26.2} */
    public static String minecraftVersion()
    {
        return NeoForgeAdapters.current().minecraftVersion();
    }

    /** @return whether the running Minecraft release is older than {@code version} */
    public static boolean isBefore(String version)
    {
        return NeoForgeAdapterSelector.compareVersions(minecraftVersion(), version) < 0;
    }
}
