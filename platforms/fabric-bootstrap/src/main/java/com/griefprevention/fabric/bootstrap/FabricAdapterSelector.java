package com.griefprevention.fabric.bootstrap;

final class FabricAdapterSelector
{
    private FabricAdapterSelector()
    {
    }

    static String adapterClassForMinecraftVersion(String minecraftVersion)
    {
        if ("1.21.11".equals(minecraftVersion))
        {
            return "com.griefprevention.fabric.GriefPreventionFabric";
        }
        return null;
    }
}
