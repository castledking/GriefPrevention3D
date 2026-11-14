package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Whether an absent owner's claims expire, as Paper's {@code CleanupUnusedClaimPreTask} and
 * {@code CleanupUnusedClaimTask} decide it. The {@code griefprevention.dontexpire} permission is left to the
 * caller, which checks it only for owners this policy would expire, since an offline lookup can be slow.
 *
 * <p>Paper's third rule, {@code Claims.Expiration.UnusedClaimDays}, can expire a single small creative-world
 * claim scored by the blocks players placed in it; the caller supplies the score and creative-world flag.
 */
public final class ClaimExpirationPolicy
{
    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    public static final int UNUSED_CLAIM_MAX_WIDTH = 25;
    public static final int UNUSED_CLAIM_MAX_HEIGHT = 25;
    public static final long UNUSED_CLAIM_INVESTMENT_THRESHOLD = 400L;

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
        EXPIRE_ALL_CLAIMS,
        /** One unused small creative claim expires after {@code UnusedClaimDays}. */
        EXPIRE_UNUSED_CLAIM
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

        public static @NotNull Decision expired(@NotNull Outcome outcome)
        {
            return new Decision(outcome, Reason.EXPIRED);
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
                    ? Decision.expired(Outcome.EXPIRE_NEW_PLAYER_CLAIM)
                    : Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        if (settings.daysInactive() > 0)
        {
            return awayLongerThan(lastSeenMillis, settings.daysInactive(), nowMillis)
                    ? Decision.expired(Outcome.EXPIRE_ALL_CLAIMS)
                    : Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        return Decision.keep(Reason.EXPIRATION_DISABLED);
    }

    /**
     * Paper's unused-claim rule: a single small, low-investment, creative-world claim may expire on its own.
     * The caller should only consult this when the owner-level rules do not already expire claims.
     */
    public static @NotNull Decision decideUnusedClaim(
            @NotNull ClaimExpirationSettings settings,
            boolean online,
            @Nullable Long lastSeenMillis,
            boolean creativeWorld,
            boolean adminClaim,
            int claimWidth,
            int claimHeight,
            @Nullable Long investmentScore,
            long nowMillis)
    {
        if (online)
        {
            return Decision.keep(Reason.ONLINE);
        }
        if (settings.unusedClaimDays() <= 0 || !creativeWorld || adminClaim)
        {
            return Decision.keep(Reason.EXPIRATION_DISABLED);
        }
        if (lastSeenMillis == null || lastSeenMillis <= 0L)
        {
            return Decision.keep(Reason.NEVER_SEEN);
        }
        if (claimWidth > UNUSED_CLAIM_MAX_WIDTH || claimHeight > UNUSED_CLAIM_MAX_HEIGHT)
        {
            return Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        if (investmentScore == null || investmentScore >= UNUSED_CLAIM_INVESTMENT_THRESHOLD)
        {
            return Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
        }
        return awayLongerThan(lastSeenMillis, settings.unusedClaimDays(), nowMillis)
                ? Decision.expired(Outcome.EXPIRE_UNUSED_CLAIM)
                : Decision.keep(Reason.NOT_AWAY_LONG_ENOUGH);
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
