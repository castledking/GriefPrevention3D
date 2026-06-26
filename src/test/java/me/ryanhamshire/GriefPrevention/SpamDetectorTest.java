package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamDetectorTest
{
    private SpamDetector detector;
    private UUID playerA;
    private UUID playerB;

    @BeforeEach
    void setUp()
    {
        detector = new SpamDetector();
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
    }

    @Test
    void normalMessageIsNotSpam()
    {
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "Hello everyone!", 1000L);

        assertNotNull(result);
        assertEquals("Hello everyone!", result.finalMessage);
        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
    }

    @Test
    void capsSpamIsLowered()
    {
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "HELLO EVERYONE HOW ARE YOU", 1000L);

        assertEquals("hello everyone how are you", result.finalMessage);
    }

    @Test
    void shortCapsMessageIsNotLowered()
    {
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "XD", 1000L);

        assertEquals("XD", result.finalMessage);
    }

    @Test
    void exactDuplicateFromAnyoneWithinTwoSecondsIsMuted()
    {
        detector.AnalyzeMessage(playerA, "hello world", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerB, "hello world", 2500L);

        assertEquals("repeat message", result.muteReason);
    }

    @Test
    void exactDuplicateFromSamePlayerWithin30sIsMuted()
    {
        detector.AnalyzeMessage(playerA, "buy my stuff", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "buy my stuff", 20000L);

        assertEquals("repeat message", result.muteReason);
    }

    @Test
    void messagesMoreThan30sApartAreNotRepeats()
    {
        detector.AnalyzeMessage(playerA, "buy my stuff", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "buy my stuff", 50000L);

        assertNull(result.muteReason);
    }

    @Test
    void messagesTooCloseTogetherIncrementSpamLevel()
    {
        detector.AnalyzeMessage(playerA, "message one", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "message two", 1500L);

        assertNull(result.muteReason);
    }

    @Test
    void highVolumeOfTextGetsMuted()
    {
        String msg1 = "the quick brown fox jumps over the lazy dog repeatedly here today now";
        String msg2 = "an entirely different paragraph about something completely unrelated blah";
        String msg3 = "yet another unique sentence that shares no resemblance to the others ok";
        String msg4 = "one more totally original message that pushes the total volume over limits";
        detector.AnalyzeMessage(playerA, msg1, 2000L);
        detector.AnalyzeMessage(playerA, msg2, 5000L);
        detector.AnalyzeMessage(playerA, msg3, 8000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, msg4, 10000L);

        assertEquals("too much chat sent in 10 seconds", result.muteReason);
    }

    @Test
    void gibberishMessageIncreasesSpamLevel()
    {
        detector.AnalyzeMessage(playerA, "normal first message here", 2000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "!@#$%^&*()!@#$%", 20000L);

        assertNull(result.muteReason);
    }

    @Test
    void gibberishMessageSetsMuteReasonWhenAlreadySpamming()
    {
        detector.AnalyzeMessage(playerA, "a", 1000L);
        detector.AnalyzeMessage(playerA, "b", 1200L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "!@#$%^&*()!@#$%", 5000L);

        assertEquals("gibberish", result.muteReason);
    }

    @Test
    void spamWarningTriggeredAtLevelFour()
    {
        detector.AnalyzeMessage(playerA, "a", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "b", 1500L);

        assertTrue(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
    }

    @Test
    void spamAnalysisResultDefaultValues()
    {
        SpamAnalysisResult result = new SpamAnalysisResult();

        assertNull(result.finalMessage);
        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
    }

    @Test
    void chatterDataTracksRecentLength()
    {
        ChatterData data = new ChatterData();
        data.AddMessage("hello world", 1000L);
        data.AddMessage("test message", 5000L);

        int total = data.getTotalRecentLength(8000L);
        assertEquals(23, total);
    }

    @Test
    void chatterDataExpiresOldMessages()
    {
        ChatterData data = new ChatterData();
        data.AddMessage("hello world", 1000L);
        data.AddMessage("test message", 5000L);

        int total = data.getTotalRecentLength(15000L);
        assertEquals(12, total);
    }

    @Test
    void chatterDataExpiresAllOldMessages()
    {
        ChatterData data = new ChatterData();
        data.AddMessage("hello world", 1000L);
        data.AddMessage("test message", 5000L);

        int total = data.getTotalRecentLength(20000L);
        assertEquals(0, total);
    }

    @Test
    void distinctMessagesFromDifferentPlayersAreNotSpam()
    {
        detector.AnalyzeMessage(playerA, "hello from player a", 1000L);
        SpamAnalysisResult result = detector.AnalyzeMessage(playerB, "hello from player b", 2000L);

        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
    }

    @Test
    void nonSpamResetsSpamLevel()
    {
        long t = 1000L;
        detector.AnalyzeMessage(playerA, "a", t);
        t += 500;
        detector.AnalyzeMessage(playerA, "b", t);

        t += 10000;
        SpamAnalysisResult result = detector.AnalyzeMessage(playerA, "normal message after a while", t);

        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
    }
}
