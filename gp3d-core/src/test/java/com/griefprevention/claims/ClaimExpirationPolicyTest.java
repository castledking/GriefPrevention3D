package com.griefprevention.claims;

import com.griefprevention.claims.ClaimExpirationPolicy.Decision;
import com.griefprevention.claims.ClaimExpirationPolicy.Outcome;
import com.griefprevention.claims.ClaimExpirationPolicy.Reason;
import org.junit.jupiter.api.Test;

import static com.griefprevention.claims.ClaimExpirationPolicy.MILLIS_PER_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClaimExpirationPolicyTest
{
    private static final long NOW = 1_800_000_000_000L;
    private static final ClaimExpirationSettings DEFAULTS = ClaimExpirationSettings.upstreamDefaults();
    /** Bigger than the 81-block claim a new player gets with the default radius of 4. */
    private static final int BIG = 400;

    @Test
    void allClaimsExpireOnceTheOwnerIsAwayLongerThanDaysInactive()
    {
        assertEquals(Outcome.EXPIRE_ALL_CLAIMS, decide(false, daysAgo(61), 0, 0, 2, BIG).outcome());

        Decision recent = decide(false, daysAgo(59), 0, 0, 2, BIG);
        assertEquals(Outcome.KEEP, recent.outcome());
        assertEquals(Reason.NOT_AWAY_LONG_ENOUGH, recent.reason());
    }

    @Test
    void onlineAndNeverSeenOwnersKeepTheirClaims()
    {
        assertEquals(Reason.ONLINE, decide(true, daysAgo(400), 0, 0, 1, BIG).reason());
        // Paper keeps the claims of a player without a last-played time.
        assertEquals(Reason.NEVER_SEEN, decide(false, null, 0, 0, 1, BIG).reason());
        assertEquals(Reason.NEVER_SEEN, decide(false, 0L, 0, 0, 1, BIG).reason());
    }

    @Test
    void enoughBonusOrTotalClaimBlocksExemptTheOwner()
    {
        assertEquals(Reason.CLAIM_BLOCKS_EXEMPTION, decide(false, daysAgo(400), 0, 5000, 1, BIG).reason());
        assertEquals(Reason.CLAIM_BLOCKS_EXEMPTION, decide(false, daysAgo(400), 9000, 1000, 1, BIG).reason());
        assertEquals(Outcome.EXPIRE_ALL_CLAIMS, decide(false, daysAgo(400), 8999, 1000, 1, BIG).outcome());
    }

    @Test
    void aLoneNewPlayerClaimAnswersToChestClaimDaysAlone()
    {
        assertEquals(Outcome.EXPIRE_NEW_PLAYER_CLAIM, decide(false, daysAgo(8), 0, 0, 1, 81).outcome());
        // Within the week it stays, even though DaysInactive is never consulted for it.
        assertEquals(Reason.NOT_AWAY_LONG_ENOUGH, decide(false, daysAgo(6), 0, 0, 1, 81).reason());
        // A second claim, or a bigger one, puts the owner back under DaysInactive.
        assertEquals(Reason.NOT_AWAY_LONG_ENOUGH, decide(false, daysAgo(8), 0, 0, 2, 81).reason());
        assertEquals(Reason.NOT_AWAY_LONG_ENOUGH, decide(false, daysAgo(8), 0, 0, 1, 82).reason());
    }

    @Test
    void zeroDaysTurnsEachRuleOff()
    {
        ClaimExpirationSettings off = new ClaimExpirationSettings(0, 0, 10000, 5000, 4, 60);
        assertEquals(Reason.EXPIRATION_DISABLED,
                ClaimExpirationPolicy.decide(off, false, daysAgo(400), 0, 0, 1, 81, NOW).reason());

        ClaimExpirationSettings noChestRule = new ClaimExpirationSettings(0, 60, 10000, 5000, 4, 60);
        assertEquals(Outcome.EXPIRE_ALL_CLAIMS,
                ClaimExpirationPolicy.decide(noChestRule, false, daysAgo(61), 0, 0, 1, 81, NOW).outcome());
    }

    @Test
    void reportsWhenTheRuleThatAppliesRunsOut()
    {
        long seen = daysAgo(10);
        assertEquals(seen + 60 * MILLIS_PER_DAY, ClaimExpirationPolicy.expiresAtMillis(DEFAULTS, seen, 2, BIG));
        assertEquals(seen + 7 * MILLIS_PER_DAY, ClaimExpirationPolicy.expiresAtMillis(DEFAULTS, seen, 1, 81));
        assertNull(ClaimExpirationPolicy.expiresAtMillis(
                new ClaimExpirationSettings(0, 0, 0, 0, 4, 60), seen, 2, BIG));
    }

    private static Decision decide(boolean online, Long lastSeen, int accrued, int bonus, int claims, int firstArea)
    {
        return ClaimExpirationPolicy.decide(DEFAULTS, online, lastSeen, accrued, bonus, claims, firstArea, NOW);
    }

    private static long daysAgo(int days)
    {
        return NOW - days * MILLIS_PER_DAY;
    }
}
