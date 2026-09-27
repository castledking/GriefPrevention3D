package com.griefprevention.fabric.bootstrap;

import net.fabricmc.api.ModInitializer;

import java.util.List;

public final class UniversalFabricBootstrap implements ModInitializer
{
    /**
     * When true, loads every class the selected adapter's mixins target during startup. Mixins
     * otherwise apply lazily, the first time the game touches a target, so a broken injection
     * point would stay hidden until that moment. The boot smoke tests set this.
     */
    static final String VERIFY_MIXINS_PROPERTY = "griefprevention3d.verifyMixins";

    @Override
    public void onInitialize()
    {
        FabricAdapters.Selection selection = FabricAdapters.current();
        FabricAdapter adapter = selection.selected();
        if (adapter == null)
        {
            throw selection.unavailable();
        }

        try
        {
            Object candidate = Class.forName(adapter.entrypoint(), true, this.getClass().getClassLoader())
                    .getDeclaredConstructor()
                    .newInstance();
            if (!(candidate instanceof FabricPlatformAdapter))
            {
                throw new IllegalStateException(
                        "Fabric adapter " + adapter.entrypoint() + " does not implement the bootstrap contract."
                );
            }
            ((FabricPlatformAdapter) candidate).onInitialize();
        }
        catch (ReflectiveOperationException exception)
        {
            throw new IllegalStateException(
                    "Could not load the GriefPrevention3D Fabric adapter " + adapter
                            + " for Minecraft " + selection.minecraftVersion() + ".",
                    exception
            );
        }

        if (Boolean.getBoolean(VERIFY_MIXINS_PROPERTY))
        {
            verifyMixins(adapter);
        }
    }

    private void verifyMixins(FabricAdapter adapter)
    {
        List<String> targets = AdapterMixinGate.appliedTargets();
        if (targets.isEmpty())
        {
            throw new IllegalStateException("No GriefPrevention3D mixins were enabled for " + adapter + ".");
        }

        ClassLoader loader = this.getClass().getClassLoader();
        for (String target : targets)
        {
            try
            {
                // Loading without initializing still runs the mixin transformer.
                Class.forName(target, false, loader);
            }
            catch (ClassNotFoundException exception)
            {
                throw new IllegalStateException("Mixin target " + target + " is missing.", exception);
            }
        }
        System.out.println("GriefPrevention3D verified " + targets.size() + " mixin target(s) for " + adapter + ".");
    }
}
