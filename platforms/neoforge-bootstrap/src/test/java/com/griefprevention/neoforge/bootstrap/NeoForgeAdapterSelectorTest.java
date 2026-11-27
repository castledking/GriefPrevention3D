package com.griefprevention.neoforge.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeAdapterSelectorTest
{
    @Test
    void selectsMatchingAdapterFromMultiVersionIndex() throws Exception
    {
        Properties props = new Properties();
        props.setProperty("adapters", "mc26_1,mc1_21_11,mc1_21_10");
        props.setProperty("adapter.mc26_1.minecraft", "[26.1.2,26.4)");
        props.setProperty("adapter.mc26_1.entrypoint", "com.griefprevention.neoforge.mc26_1.GriefPreventionNeoForge");
        props.setProperty("adapter.mc26_1.mixins", "com.griefprevention.neoforge.mc26_1.mixin");
        props.setProperty("adapter.mc1_21_11.minecraft", "[1.21.11,1.21.12)");
        props.setProperty("adapter.mc1_21_11.entrypoint", "com.griefprevention.neoforge.mc1_21_11.GriefPreventionNeoForge");
        props.setProperty("adapter.mc1_21_11.mixins", "com.griefprevention.neoforge.mc1_21_11.mixin");
        props.setProperty("adapter.mc1_21_10.minecraft", "[1.21.3,1.21.11)");
        props.setProperty("adapter.mc1_21_10.entrypoint", "com.griefprevention.neoforge.mc1_21_10.GriefPreventionNeoForge");
        props.setProperty("adapter.mc1_21_10.mixins", "com.griefprevention.neoforge.mc1_21_10.mixin");

        List<NeoForgeAdapter> adapters = NeoForgeAdapterSelector.parse(props);

        assertEquals("mc1_21_11", NeoForgeAdapterSelector.select(adapters, "1.21.11").tag());
        assertEquals("mc1_21_10", NeoForgeAdapterSelector.select(adapters, "1.21.3").tag());
        assertEquals("mc1_21_10", NeoForgeAdapterSelector.select(adapters, "1.21.6").tag());
        assertEquals("mc1_21_10", NeoForgeAdapterSelector.select(adapters, "1.21.10").tag());
        assertEquals("mc26_1", NeoForgeAdapterSelector.select(adapters, "26.1.2").tag());
        assertEquals("mc26_1", NeoForgeAdapterSelector.select(adapters, "26.3").tag());

        assertNull(NeoForgeAdapterSelector.select(adapters, "1.21.1"));
        assertNull(NeoForgeAdapterSelector.select(adapters, "1.21.2"));
        assertNull(NeoForgeAdapterSelector.select(adapters, "1.21.12"));
        assertNull(NeoForgeAdapterSelector.select(adapters, "26.1"));
        assertNull(NeoForgeAdapterSelector.select(adapters, "26.1.1"));
        assertNull(NeoForgeAdapterSelector.select(adapters, "26.4"));
    }

    @Test
    void matchesMavenRangeForms()
    {
        assertTrue(NeoForgeAdapterSelector.matchesRange("1.21.11", "[1.21.11]"));
        assertFalse(NeoForgeAdapterSelector.matchesRange("1.21.10", "[1.21.11]"));
        assertTrue(NeoForgeAdapterSelector.matchesRange("26.1.2", "[1.21.11,1.21.12),[26.1,26.4)"));
        assertTrue(NeoForgeAdapterSelector.matchesRange("26.3.0-beta", "[26.1,26.4)"));
        assertFalse(NeoForgeAdapterSelector.matchesRange("26.4.0", "[26.1,26.4)"));
    }
}
