package com.griefprevention.fabric.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricAdapterSelectorTest
{
    @Test
    void selectsTheCurrentVersionAdapterWithoutLoadingIt()
    {
        assertEquals(
                "com.griefprevention.fabric.GriefPreventionFabric",
                FabricAdapterSelector.adapterClassForMinecraftVersion("1.21.11")
        );
    }

    @Test
    void rejectsVersionsWithoutAPackagedAdapter()
    {
        assertNull(FabricAdapterSelector.adapterClassForMinecraftVersion("1.21.10"));
        assertNull(FabricAdapterSelector.adapterClassForMinecraftVersion("1.14.4"));
    }
}
