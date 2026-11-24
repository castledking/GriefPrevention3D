package com.griefprevention.neoforge.bootstrap;

/**
 * Implemented by each versioned NeoForge adapter entrypoint so {@link UniversalNeoForgeBootstrap}
 * can start it without linking against Minecraft classes directly.
 */
public interface NeoForgePlatformAdapter
{
    void onInitialize();
}
