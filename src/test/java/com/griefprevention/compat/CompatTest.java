package com.griefprevention.compat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatTest
{
    @Test
    void isBlankReturnsTrueForNull()
    {
        assertTrue(Compat.isBlank(null));
    }

    @Test
    void isBlankReturnsTrueForEmptyString()
    {
        assertTrue(Compat.isBlank(""));
    }

    @Test
    void isBlankReturnsTrueForWhitespace()
    {
        assertTrue(Compat.isBlank("   "));
        assertTrue(Compat.isBlank("\t\n"));
    }

    @Test
    void isBlankReturnsFalseForNonBlank()
    {
        assertFalse(Compat.isBlank("hello"));
        assertFalse(Compat.isBlank(" a "));
    }

    @Test
    void repeatReturnsEmptyForZeroCount()
    {
        assertEquals("", Compat.repeat("abc", 0));
    }

    @Test
    void repeatReturnsEmptyForNegativeCount()
    {
        assertEquals("", Compat.repeat("abc", -1));
    }

    @Test
    void repeatReturnsEmptyForNullString()
    {
        assertEquals("", Compat.repeat(null, 5));
    }

    @Test
    void repeatConcatenatesStringNTimes()
    {
        assertEquals("***", Compat.repeat("*", 3));
        assertEquals("abcabc", Compat.repeat("abc", 2));
    }

    @Test
    void repeatOnce()
    {
        assertEquals("x", Compat.repeat("x", 1));
    }

    @Test
    void mapOfReturnsEmptyMapForNoArgs()
    {
        Map<String, String> map = Compat.mapOf();
        assertTrue(map.isEmpty());
    }

    @Test
    void mapOfCreatesSingleEntryMap()
    {
        Map<String, Integer> map = Compat.mapOf("key", 42);
        assertEquals(1, map.size());
        assertEquals(42, map.get("key"));
    }

    @Test
    void mapOfCreatesMultiEntryMap()
    {
        Map<String, Integer> map = Compat.mapOf("a", 1, "b", 2, "c", 3);
        assertEquals(3, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
        assertEquals(3, map.get("c"));
    }
}
