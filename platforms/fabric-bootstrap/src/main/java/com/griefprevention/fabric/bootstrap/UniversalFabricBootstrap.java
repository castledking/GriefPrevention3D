package com.griefprevention.fabric.bootstrap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class UniversalFabricBootstrap implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        ModContainer minecraft = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("Fabric Loader did not expose the Minecraft version."));
        String minecraftVersion = minecraft.getMetadata().getVersion().getFriendlyString();
        String adapterClass = FabricAdapterSelector.adapterClassForMinecraftVersion(minecraftVersion);
        if (adapterClass == null)
        {
            throw new IllegalStateException(
                    "This GriefPrevention3D jar does not contain a Fabric adapter for Minecraft "
                            + minecraftVersion + "."
            );
        }

        try
        {
            Object candidate = Class.forName(adapterClass, true, this.getClass().getClassLoader())
                    .getDeclaredConstructor()
                    .newInstance();
            if (!(candidate instanceof FabricPlatformAdapter))
            {
                throw new IllegalStateException(
                        "Fabric adapter " + adapterClass + " does not implement the bootstrap contract."
                );
            }
            ((FabricPlatformAdapter) candidate).onInitialize();
        }
        catch (ReflectiveOperationException exception)
        {
            throw new IllegalStateException(
                    "Could not load the GriefPrevention3D Fabric adapter for Minecraft "
                            + minecraftVersion + ".",
                    exception
            );
        }
    }
}
