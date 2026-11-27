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

    /**
     * Reads the running Minecraft version from FML, which moved it between the releases served:
     * FML 10 (NeoForge 21.9 and later) keeps it on the {@code FMLLoader.getCurrent()} instance,
     * earlier FML on the static {@code FMLLoader.versionInfo()}. Both are reached reflectively so
     * that neither shape is linked against a loader that lacks it.
     */
    static String detectMinecraftVersion()
    {
        String version = versionInfoMcVersion(invoke(invoke(FMLLoader.class, null, "getCurrent"), "getVersionInfo"));
        if (version == null)
        {
            version = versionInfoMcVersion(invoke(FMLLoader.class, null, "versionInfo"));
        }
        if (version == null)
        {
            throw new IllegalStateException("GriefPrevention3D could not determine the running Minecraft version on NeoForge.");
        }
        return version;
    }

    private static String versionInfoMcVersion(Object versionInfo)
    {
        Object version = invoke(versionInfo, "mcVersion");
        return version == null || version.toString().isEmpty() ? null : version.toString();
    }

    private static Object invoke(Object target, String method)
    {
        return target == null ? null : invoke(target.getClass(), target, method);
    }

    private static Object invoke(Class<?> owner, Object target, String method)
    {
        try
        {
            Method found = owner.getMethod(method);
            return found.invoke(target);
        }
        catch (ReflectiveOperationException | RuntimeException absent)
        {
            return null;
        }
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
