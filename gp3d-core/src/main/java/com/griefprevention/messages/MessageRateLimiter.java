package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-compatible throttle for player-facing error messages.
 *
 * <p>Matches {@code GriefPrevention.sendRateLimitedErrorMessage}: one shared cooldown per player
 * rather than one per message, so a player denied repeatedly across different actions still sees at
 * most one message per window.
 */
public final class MessageRateLimiter
{
    /** Upstream's {@code ERROR_MESSAGE_COOLDOWN_MS}. */
    public static final long DEFAULT_COOLDOWN_MILLIS = 10_000L;

    private final ConcurrentHashMap<UUID, Long> lastMessageMillis = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public MessageRateLimiter()
    {
        this(DEFAULT_COOLDOWN_MILLIS);
    }

    public MessageRateLimiter(long cooldownMillis)
    {
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * Consumes the player's message budget for this window.
     *
     * @param playerId the recipient
     * @param nowMillis the current wall-clock time
     * @return true when the message should be sent, false when it falls inside the cooldown
     */
    public boolean tryAcquire(@NotNull UUID playerId, long nowMillis)
    {
        Long previous = this.lastMessageMillis.get(playerId);
        if (previous != null && nowMillis - previous < this.cooldownMillis)
        {
            return false;
        }
        this.lastMessageMillis.put(playerId, nowMillis);
        return true;
    }

    /**
     * Drops a player's cooldown state. Called on disconnect so the map does not grow without bound;
     * a reconnecting player starts a fresh window, which upstream also does per server restart.
     */
    public void forget(@NotNull UUID playerId)
    {
        this.lastMessageMillis.remove(playerId);
    }

    public void clear()
    {
        this.lastMessageMillis.clear();
    }
}
