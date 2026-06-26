package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordFinderTest
{
    @Test
    void emptyWordListNeverMatches()
    {
        WordFinder finder = new WordFinder(Collections.emptyList());

        assertFalse(finder.hasMatch("hello world"));
        assertFalse(finder.hasMatch("anything at all"));
    }

    @Test
    void matchesExactWord()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("banned"));

        assertTrue(finder.hasMatch("this is a banned word"));
        assertTrue(finder.hasMatch("banned"));
    }

    @Test
    void doesNotMatchPartialWord()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("ban"));

        assertFalse(finder.hasMatch("banana"));
    }

    @Test
    void matchIsCaseInsensitive()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("spam"));

        assertTrue(finder.hasMatch("This is SPAM"));
        assertTrue(finder.hasMatch("Spam message"));
    }

    @Test
    void matchesAtStartOfString()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("hello"));

        assertTrue(finder.hasMatch("hello there"));
    }

    @Test
    void matchesAtEndOfString()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("goodbye"));

        assertTrue(finder.hasMatch("say goodbye"));
    }

    @Test
    void multipleWordsAreSearched()
    {
        WordFinder finder = new WordFinder(Arrays.asList("apple", "banana", "cherry"));

        assertTrue(finder.hasMatch("I like banana"));
        assertTrue(finder.hasMatch("cherry pie"));
        assertTrue(finder.hasMatch("eat an apple"));
        assertFalse(finder.hasMatch("I like grapes"));
    }

    @Test
    void censorReplacesWordWithAsterisks()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("bad"));

        String result = finder.censor("this is bad stuff");
        assertEquals("this is *** stuff", result);
    }

    @Test
    void censorReplacesMultipleWords()
    {
        WordFinder finder = new WordFinder(Arrays.asList("bad", "ugly"));

        String result = finder.censor("bad and ugly things");
        assertEquals("*** and **** things", result);
    }

    @Test
    void censorPreservesNonMatchingText()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("secret"));

        String result = finder.censor("this is a normal message");
        assertEquals("this is a normal message", result);
    }

    @Test
    void censorHandlesNullInput()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("bad"));

        assertEquals(null, finder.censor(null));
    }

    @Test
    void censorHandlesEmptyInput()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("bad"));

        assertEquals("", finder.censor(""));
    }

    @Test
    void censorWithEmptyWordListReturnsInput()
    {
        WordFinder finder = new WordFinder(Collections.emptyList());

        assertEquals("hello world", finder.censor("hello world"));
    }

    @Test
    void blankWordsInListAreIgnored()
    {
        WordFinder finder = new WordFinder(Arrays.asList("", "  ", "valid"));

        assertTrue(finder.hasMatch("this is valid text"));
        assertFalse(finder.hasMatch("nothing here"));
    }

    @Test
    void listOfOnlyBlankWordsNeverMatches()
    {
        WordFinder finder = new WordFinder(Arrays.asList("", "   "));

        assertFalse(finder.hasMatch("anything"));
    }
}
