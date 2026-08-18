package com.griefprevention.messages;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTextTest
{
    private static final char S = LegacyText.SECTION;

    @Test
    void translatesAmpersandAndDollarPrefixes()
    {
        assertEquals(S + "6Warning " + S + "7and " + S + "fwhite",
                LegacyText.translate("&6Warning $7and &fwhite"));
    }

    @Test
    void translatesEscapedNewlines()
    {
        assertEquals("first\nsecond", LegacyText.translate("first\\nsecond"));
    }

    @Test
    void translatesHexColorsToTheLegacyEncoding()
    {
        assertEquals(S + "x" + S + "a" + S + "1" + S + "b" + S + "2" + S + "c" + S + "3" + "text",
                LegacyText.translate("&#A1B2C3text"));
        assertEquals(S + "x" + S + "a" + S + "1" + S + "b" + S + "2" + S + "c" + S + "3" + "text",
                LegacyText.translate("#a1b2c3text"));
    }

    @Test
    void stripsEveryCodeShape()
    {
        assertEquals("Warning and white",
                LegacyText.strip(LegacyText.translate("&6Warning &land &r&fwhite")));
        assertEquals("text", LegacyText.strip(LegacyText.translate("&#A1B2C3text")));
    }

    @Test
    void recognisesDisabledMessages()
    {
        assertTrue(LegacyText.isDisabled(""));
        assertTrue(LegacyText.isDisabled("   "));
        assertTrue(LegacyText.isDisabled(LegacyText.translate("&c")));
        assertFalse(LegacyText.isDisabled(LegacyText.translate("&cdenied")));
    }

    @Test
    void plainTextIsOneInheritingSegment()
    {
        List<LegacySegment> segments = LegacyText.segments("plain");

        assertEquals(1, segments.size());
        assertEquals("plain", segments.get(0).text());
        assertNull(segments.get(0).color());
        assertFalse(segments.get(0).bold());
    }

    @Test
    void splitsOnColorChanges()
    {
        List<LegacySegment> segments = LegacyText.segments(LegacyText.translate("&6warn&7ing"));

        assertEquals(2, segments.size());
        assertEquals("warn", segments.get(0).text());
        assertEquals("6", segments.get(0).color());
        assertEquals("ing", segments.get(1).text());
        assertEquals("7", segments.get(1).color());
    }

    @Test
    void stylesAccumulateUntilAColorResetsThem()
    {
        List<LegacySegment> segments = LegacyText.segments(LegacyText.translate("&l&nbold&cplain"));

        assertEquals(2, segments.size());
        assertTrue(segments.get(0).bold());
        assertTrue(segments.get(0).underlined());
        assertEquals("plain", segments.get(1).text());
        assertEquals("c", segments.get(1).color());
        assertFalse(segments.get(1).bold());
        assertFalse(segments.get(1).underlined());
    }

    @Test
    void resetClearsColorAndStyle()
    {
        List<LegacySegment> segments = LegacyText.segments(LegacyText.translate("&c&lloud&rquiet"));

        assertEquals(2, segments.size());
        assertNull(segments.get(1).color());
        assertFalse(segments.get(1).bold());
    }

    @Test
    void readsHexColoredSegments()
    {
        List<LegacySegment> segments = LegacyText.segments(LegacyText.translate("&#A1B2C3fancy"));

        assertEquals(1, segments.size());
        assertEquals("#a1b2c3", segments.get(0).color());
        assertEquals("fancy", segments.get(0).text());
    }

    @Test
    void dropsRunsWithNoText()
    {
        assertTrue(LegacyText.segments(LegacyText.translate("&c&l")).isEmpty());
        assertTrue(LegacyText.segments("").isEmpty());
    }

    @Test
    void keepsUnknownAndTruncatedCodesAsText()
    {
        assertEquals(S + "ztext", textOf(LegacyText.segments(LegacyText.translate("&ztext"))));
        assertEquals(String.valueOf(S), textOf(LegacyText.segments(String.valueOf(S))));
    }

    @Test
    void truncatedHexDegradesToTextAndAValidTrailingCode()
    {
        // The §x prefix is incomplete, so it renders literally; the §a that follows is still a color.
        List<LegacySegment> segments = LegacyText.segments(S + "x" + S + "a1");

        assertEquals(2, segments.size());
        assertEquals(S + "x", segments.get(0).text());
        assertNull(segments.get(0).color());
        assertEquals("1", segments.get(1).text());
        assertEquals("a", segments.get(1).color());
    }

    @Test
    void newlinesStayInsideSegmentText()
    {
        List<LegacySegment> segments = LegacyText.segments(LegacyText.translate("&cfirst\\nsecond"));

        assertEquals(1, segments.size());
        assertEquals("first\nsecond", segments.get(0).text());
    }

    private static String textOf(List<LegacySegment> segments)
    {
        StringBuilder text = new StringBuilder();
        for (LegacySegment segment : segments)
        {
            text.append(segment.text());
        }
        return text.toString();
    }
}
