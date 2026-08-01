package com.griefprevention.fabric.bootstrap;

/**
 * Stable Java 8 boundary between Fabric Loader and a Minecraft-version-specific adapter.
 */
public interface FabricPlatformAdapter
{
    void onInitialize();
}
