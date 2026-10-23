package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Whether an absent owner's claims expire, as Paper's {@code CleanupUnusedClaimPreTask} and
 * {@code CleanupUnusedClaimTask} decide it. The {@code griefprevention.dontexpire} permission is left to the
 * caller, which checks it only for owners this policy would expire, since an offline lookup can be slow.
 *
 * <p>Paper's third rule, {@code Claims.Expiration.UnusedClaimDays}, scores the blocks players placed in small
 * creative-world claims and is not decided here.
 */
public final class ClaimExpirationPolicy
{
    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private ClaimExpirationPolicy()
    {
    }

    /** What happens to an owner's claims. */
    public enum Outcome
    {
        /** Nothing expires. */
        KEEP,
        /** The owner's only claim, no bigger than a new player's claim, expires after {@code ChestClaimDays}. */
        EXPIRE_NEW_PLAYER_CLAIM,
        /** Every claim the owner has expires after {@code AllClaims.DaysInactive}. */
        EXPIRE_ALL_CLAIMS
    }

    /** Why nothing expired, or that something did. */
    public enum Reason
    {
        ONLINE,
        NEVER_SEEN,
        CLAIM_BLOCKS_EXEMPTION,
        NO_CLAIMS,
        NOT_AWAY_LONG_ENOUGH,
        EXPIRATION_DISABLED,
        EXPIRED
    }

    /** The outcome and why. */
    public static final class Decision
    {
        private final @NotNull Outcome outcome;
        private final @NotNull Reason reason;

        private Decision(@NotNull Outcome outcome, @NotNull Reason reason)
        {
            this.outcome = outcome;
            this.reason = reason;
        }

        private static @NotNull Decision keep(@NotNull Reason reason)
        {
            return new Decision(Outcome.KEEP, reason);
        }

        public @NotNull Outcome outcome()
        {
            return this.outcome;
        }

        public @NotNull Reason reason()
        {
            return this.reason;
        }

        public boolean expires()
        {
            return this.outcome != Outcome.KEEP;
        }
    }

    /**
     * @param online whether the owner is on the server now
     * @param lastSeenMillis when the owner was last online, or null when that is unknown, which keeps their
     *        claims as Paper does for a player without a last-played time
     * @param accruedClaimBlocks the owner's accrued claim blocks
     * @param bonusClaimBlocks the owner's bonus claim blocks, not counting permission-group bonuses
     * @param topLevelClaimCount how many top-level claims the owner has
     * @param firstClaimArea the area of the owner's first claim, which is the one a lone claim is
     * @param nowMillis the current time
     */
    public static @NotNull Decision decide(
            @NotNull ClaimExpirationSettings settings,
            boolean online,
            @Nullable Long lastSeenMillis,
            int accruedClaimBlocks,
            int bonusClaimBlocks,
            int topLevelClaimCount,
            int firstClaimArea,
            long nowMillis)
    {
        if (online)
        {
            return Decision.keep(Reason.ONLINE);
        }
        if (lastSeenMillis == null || lastSeenMillis <= 0L)
        {
            return Decision.keep(Reason.NEVER_SEEN);
        }
        if (bonusClaimBlocks >= settings.exceptWhenOwnerHasBonusClaimBlocks()
                || (long) bonusClaimBlocks + accruedClaimBlocks >= settings.exceptWhenOwnerHasTotalClaimBlocks())
        {
            return Decision.keep(Reason.CLAIM_BLOCKS_EXEMPTION);
        }
        if (topLevelClaimCount <= 0)
        {
            return Decision.keep(Reason.NO_CLAIMS);
        }

        // As on Paper, an owner whose only claim is a new player's claim answers to ChestClaimDays alone.
        if (topLevelClaimCount == 1
                && firstClaimArea <= settings.newPlayerClaimArea()
                && settings.chestClaimDays() > 0)
        {
            return awayLongerThan(lastSeenMillis, settings.chestClaimDays(), nowMillis)
                    ? new Decision(Outcome.EXPIRE_NEW_PLAYER_CLAIM, Reason.EXPIRED)
                    : Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        if (settings.daysInactive() > 0)
        {
            return awayLongerThan(lastSeenMillis, settings.daysInactive(), nowMillis)
                    ? new Decision(Outcome.EXPIRE_ALL_CLAIMS, Reason.EXPIRED)
                    : Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        return Decision.keep(Reason.EXPIRATION_DISABLED);
    }

    /** When the owner's claims would expire under the rule that applies to them, or null when none does. */
    public static @Nullable Long expiresAtMillis(
            @NotNull ClaimExpirationSettings settings,
            long lastSeenMillis,
            int topLevelClaimCount,
            int firstClaimArea)
    {
        if (topLevelClaimCount == 1
                && firstClaimArea <= settings.newPlayerClaimArea()
                && settings.chestClaimDays() > 0)
        {
            return lastSeenMillis + settings.chestClaimDays() * MILLIS_PER_DAY;
        }
        if (topLevelClaimCount > 0 && settings.daysInactive() > 0)
        {
            return lastSeenMillis + settings.daysInactive() * MILLIS_PER_DAY;
        }
        return null;
    }

    private static boolean awayLongerThan(long lastSeenMillis, int days, long nowMillis)
    {
        return nowMillis - days * MILLIS_PER_DAY > lastSeenMillis;
    }
}
