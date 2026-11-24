package com.griefprevention.neoforge.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeAdapterSelectorTest
{
    @Test
    void selectsMatchingAdapterFromMultiVersionIndex() throws Exception
    {
        Properties props = new Properties();
        props.setProperty("adapters", "mc26_1,mc1_21_11");
        props.setProperty("adapter.mc26_1.minecraft", "[26.1,26.4)");
        props.setProperty("adapter.mc26_1.entrypoint", "com.griefprevention.neoforge.mc26_1.GriefPreventionNeoForge");
        props.setProperty("adapter.mc26_1.mixins", "com.griefprevention.neoforge.mc26_1.mixin");
        props.setProperty("adapter.mc1_21_11.minecraft", "[1.21.11,1.21.12)");
        props.setProperty("adapter.mc1_21_11.entrypoint", "com.griefprevention.neoforge.mc1_21_11.GriefPreventionNeoForge");
        props.setProperty("adapter.mc1_21_11.mixins", "com.griefprevention.neoforge.mc1_21_11.mixin");

        List<NeoForgeAdapter> adapters = NeoForgeAdapterSelector.parse(props);

        NeoForgeAdapter mc12111 = NeoForgeAdapterSelector.select(adapters, "1.21.11");
        assertNotNull(mc12111);
        assertEquals("mc1_21_11", mc12111.tag());

        NeoForgeAdapter mc261 = NeoForgeAdapterSelector.select(adapters, "26.1");
        assertNotNull(mc261);
        assertEquals("mc26_1", mc261.tag());

        NeoForgeAdapter mc2612 = NeoForgeAdapterSelector.select(adapters, "26.1.2");
        assertNotNull(mc2612);
        assertEquals("mc26_1", mc2612.tag());

        NeoForgeAdapter mc263 = NeoForgeAdapterSelector.select(adapters, "26.3");
        assertNotNull(mc263);
        assertEquals("mc26_1", mc263.tag());

        assertNull(NeoForgeAdapterSelector.select(adapters, "1.21.10"));
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
