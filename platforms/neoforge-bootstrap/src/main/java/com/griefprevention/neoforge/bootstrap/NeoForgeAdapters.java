package com.griefprevention.neoforge.bootstrap;

import net.neoforged.fml.loading.FMLLoader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Resolves the bundled NeoForge adapter for the running Minecraft release once and caches the
 * decision for both {@link NeoForgeAdapterMixinGate} and {@link UniversalNeoForgeBootstrap}.
 */
final class NeoForgeAdapters
{
    private static volatile Selection cached;

    private NeoForgeAdapters()
    {
    }

    static Selection current()
    {
        Selection existing = cached;
        if (existing != null)
        {
            return existing;
        }
        Selection resolved = resolve();
        cached = resolved;
        return resolved;
    }

    private static Selection resolve()
    {
        String minecraftVersion = detectMinecraftVersion();
        List<NeoForgeAdapter> adapters;
        try
        {
            adapters = NeoForgeAdapterSelector.readIndex(NeoForgeAdapters.class.getClassLoader());
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                    "GriefPrevention3D could not read its NeoForge adapter index.",
                    exception
            );
        }

        NeoForgeAdapter selected = NeoForgeAdapterSelector.select(adapters, minecraftVersion);
        return new Selection(minecraftVersion, adapters, selected);
    }

    static String detectMinecraftVersion()
    {
        try
        {
            FMLLoader loader = FMLLoader.getCurrent();
            if (loader != null && loader.getVersionInfo() != null)
            {
                String version = loader.getVersionInfo().mcVersion();
                if (version != null && !version.isEmpty())
                {
                    return version;
                }
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to older NeoForge 21.x static FMLLoader.versionInfo() lookup.
        }

        try
        {
            Method versionInfo = FMLLoader.class.getMethod("versionInfo");
            Object info = versionInfo.invoke(null);
            if (info != null)
            {
                Object version = info.getClass().getMethod("mcVersion").invoke(info);
                if (version != null && !version.toString().isEmpty())
                {
                    return version.toString();
                }
            }
        }
        catch (Throwable ignored) {}

        throw new IllegalStateException("GriefPrevention3D could not determine the running Minecraft version on NeoForge.");
    }

    static final class Selection
    {
        private final String minecraftVersion;
        private final List<NeoForgeAdapter> adapters;
        private final NeoForgeAdapter selected;

        Selection(String minecraftVersion, List<NeoForgeAdapter> adapters, NeoForgeAdapter selected)
        {
            this.minecraftVersion = minecraftVersion;
            this.adapters = adapters;
            this.selected = selected;
        }

        String minecraftVersion()
        {
            return this.minecraftVersion;
        }

        NeoForgeAdapter selected()
        {
            return this.selected;
        }

        IllegalStateException unavailable()
        {
            return new IllegalStateException(
                    "GriefPrevention3D does not support Minecraft " + this.minecraftVersion
                            + " on NeoForge. Bundled adapters cover: " + NeoForgeAdapterSelector.describe(this.adapters) + "."
            );
        }
    }
}
