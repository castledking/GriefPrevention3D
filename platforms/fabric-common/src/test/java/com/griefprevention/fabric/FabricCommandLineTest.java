package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricCommandLineTest
{
    @Test
    void splitsArgumentsTheWayBukkitDoes()
    {
        assertArrayEquals(new String[0], FabricCommandLine.split(""));
        assertArrayEquals(new String[0], FabricCommandLine.split("   "));
        assertArrayEquals(new String[] {"[vip.node]", "build"}, FabricCommandLine.split("[vip.node]  build "));
        assertArrayEquals(new String[] {"Steve", "-250"}, FabricCommandLine.split("Steve -250"));
    }

    @Test
    void completionTracksTheArgumentBeingTyped()
    {
        FabricCommandLine.Completion empty = FabricCommandLine.completion("");
        assertArrayEquals(new String[0], empty.previous());
        assertEquals("", empty.current());
        assertEquals(0, empty.currentStart());

        FabricCommandLine.Completion partial = FabricCommandLine.completion("bonus Ste");
        assertArrayEquals(new String[] {"bonus"}, partial.previous());
        assertEquals("Ste", partial.current());
        assertEquals(6, partial.currentStart());

        FabricCommandLine.Completion next = FabricCommandLine.completion("bonus Steve ");
        assertArrayEquals(new String[] {"bonus", "Steve"}, next.previous());
        assertEquals("", next.current());
        assertEquals(12, next.currentStart());
    }

    @Test
    void matchingIgnoresCaseAndRepeats()
    {
        List<String> options = Arrays.asList("access", "Accrued", "build", "access");

        assertEquals(Arrays.asList("access", "Accrued"), FabricCommandLine.matching(options, "AC"));
        assertEquals(Arrays.asList("access", "Accrued", "build"), FabricCommandLine.matching(options, ""));
    }
}
