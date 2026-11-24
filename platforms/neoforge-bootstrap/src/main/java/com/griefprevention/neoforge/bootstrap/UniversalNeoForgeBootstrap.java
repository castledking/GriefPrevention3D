package com.griefprevention.neoforge.bootstrap;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import java.util.List;

/**
 * Single NeoForge mod entrypoint that dispatches to the bundled adapter matching the running
 * Minecraft version (1.21.11 or 26.1–26.3).
 */
@Mod(UniversalNeoForgeBootstrap.MOD_ID)
public final class UniversalNeoForgeBootstrap
{
    public static final String MOD_ID = "griefprevention3d";
    static final String VERIFY_MIXINS_PROPERTY = "griefprevention3d.verifyMixins";

    public UniversalNeoForgeBootstrap(IEventBus modEventBus, ModContainer modContainer)
    {
        NeoForgeAdapters.Selection selection = NeoForgeAdapters.current();
        NeoForgeAdapter adapter = selection.selected();
        if (adapter == null)
        {
            throw selection.unavailable();
        }

        try
        {
            Object candidate = Class.forName(adapter.entrypoint(), true, this.getClass().getClassLoader())
                    .getDeclaredConstructor()
                    .newInstance();
            if (!(candidate instanceof NeoForgePlatformAdapter))
            {
                throw new IllegalStateException(
                        "NeoForge adapter " + adapter.entrypoint() + " does not implement the bootstrap contract."
                );
            }
            ((NeoForgePlatformAdapter) candidate).onInitialize();
        }
        catch (ReflectiveOperationException exception)
        {
            throw new IllegalStateException(
                    "Could not load the GriefPrevention3D NeoForge adapter " + adapter
                            + " for Minecraft " + selection.minecraftVersion() + ".",
                    exception
            );
        }

        if (Boolean.getBoolean(VERIFY_MIXINS_PROPERTY))
        {
            verifyMixins(adapter);
        }
    }

    private void verifyMixins(NeoForgeAdapter adapter)
    {
        List<String> targets = NeoForgeAdapterMixinGate.appliedTargets();
        if (targets.isEmpty())
        {
            throw new IllegalStateException("No GriefPrevention3D mixins were enabled for " + adapter + ".");
        }

        ClassLoader loader = this.getClass().getClassLoader();
        for (String target : targets)
        {
            try
            {
                Class.forName(target, false, loader);
            }
            catch (ClassNotFoundException exception)
            {
                throw new IllegalStateException("Mixin target " + target + " is missing.", exception);
            }
        }
        System.out.println("GriefPrevention3D verified " + targets.size() + " NeoForge mixin target(s) for " + adapter + ".");
    }
}
