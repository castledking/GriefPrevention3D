package com.griefprevention.fabric.bootstrap;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricAdapterSelectorTest
{
    private static final FabricAdapterSelector.VersionMatcher FABRIC_SEMANTICS = (version, predicate) -> {
        try
        {
            return VersionPredicate.parse(predicate).test(Version.parse(version));
        }
        catch (Exception exception)
        {
            throw new AssertionError(exception);
        }
    };

    @Test
    void selectsTheAdapterWhoseRangeIncludesTheRunningVersion() throws IOException
    {
        List<FabricAdapter> adapters = FabricAdapterSelector.parse(universalIndex());

        assertEquals("mc1_21_11", select(adapters, "1.21.11").tag());
        for (String version : new String[]{"26.1", "26.1.1", "26.1.2", "26.2", "26.3", "26.3.1"})
        {
            assertEquals("mc26_1", select(adapters, version).tag(), version);
        }
    }

    @Test
    void rejectsVersionsNoAdapterWasBuiltFor() throws IOException
    {
        List<FabricAdapter> adapters = FabricAdapterSelector.parse(universalIndex());

        assertNull(select(adapters, "1.21.10"));
        assertNull(select(adapters, "1.14.4"));
        // Snapshots of the next release have not been verified and must not load.
        assertNull(select(adapters, "26.4-alpha.26.39.a"));
        assertNull(select(adapters, "26.4"));
    }

    @Test
    void mixinPackagesMatchWhetherOrNotMixinAppendsADot() throws IOException
    {
        FabricAdapter adapter = select(FabricAdapterSelector.parse(universalIndex()), "26.2");

        assertTrue(adapter.ownsMixinPackage("com.griefprevention.fabric.mc26_1.mixin"));
        assertTrue(adapter.ownsMixinPackage("com.griefprevention.fabric.mc26_1.mixin."));
        assertFalse(adapter.ownsMixinPackage("com.griefprevention.fabric.mixin."));
    }

    @Test
    void anIndexWithAMissingFieldIsRejected()
    {
        Properties properties = universalIndex();
        properties.remove("adapter.mc26_1.entrypoint");

        IOException failure = assertThrows(IOException.class, () -> FabricAdapterSelector.parse(properties));
        assertTrue(failure.getMessage().contains("adapter.mc26_1.entrypoint"));
    }

    @Test
    void describesTheSupportedRangesForTheStartupError() throws IOException
    {
        assertEquals(
                ">=26.1 <26.4-, 1.21.11",
                FabricAdapterSelector.describe(FabricAdapterSelector.parse(universalIndex()))
        );
    }

    private static FabricAdapter select(List<FabricAdapter> adapters, String version)
    {
        return FabricAdapterSelector.select(adapters, version, FABRIC_SEMANTICS);
    }

    private static Properties universalIndex()
    {
        Properties properties = new Properties();
        properties.setProperty("adapters", "mc26_1,mc1_21_11");
        properties.setProperty("adapter.mc26_1.minecraft", ">=26.1 <26.4-");
        properties.setProperty("adapter.mc26_1.entrypoint", "com.griefprevention.fabric.mc26_1.GriefPreventionFabric");
        properties.setProperty("adapter.mc26_1.mixins", "com.griefprevention.fabric.mc26_1.mixin");
        properties.setProperty("adapter.mc1_21_11.minecraft", "1.21.11");
        properties.setProperty("adapter.mc1_21_11.entrypoint", "com.griefprevention.fabric.GriefPreventionFabric");
        properties.setProperty("adapter.mc1_21_11.mixins", "com.griefprevention.fabric.mixin");
        return properties;
    }
}
