package com.griefprevention.messages;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRateLimiterTest
{
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void firstMessagePasses()
    {
        assertTrue(new MessageRateLimiter().tryAcquire(PLAYER, 0L));
    }

    @Test
    void suppressesInsideTheCooldownWindow()
    {
        MessageRateLimiter limiter = new MessageRateLimiter();

        assertTrue(limiter.tryAcquire(PLAYER, 1_000L));
        assertFalse(limiter.tryAcquire(PLAYER, 1_001L));
        assertFalse(limiter.tryAcquire(PLAYER, 10_999L));
    }

    @Test
    void allowsAgainExactlyAtTheCooldownBoundary()
    {
        MessageRateLimiter limiter = new MessageRateLimiter();

        assertTrue(limiter.tryAcquire(PLAYER, 1_000L));
        assertTrue(limiter.tryAcquire(PLAYER, 11_000L));
    }

    @Test
    void suppressedAttemptsDoNotExtendTheWindow()
    {
        MessageRateLimiter limiter = new MessageRateLimiter();

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 9_000L));
        assertTrue(limiter.tryAcquire(PLAYER, 10_000L));
    }

    @Test
    void cooldownIsPerPlayer()
    {
        MessageRateLimiter limiter = new MessageRateLimiter();

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(OTHER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 5_000L));
    }

    @Test
    void forgettingAPlayerStartsAFreshWindow()
    {
        MessageRateLimiter limiter = new MessageRateLimiter();

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        limiter.forget(PLAYER);

        assertTrue(limiter.tryAcquire(PLAYER, 1L));
    }
}
