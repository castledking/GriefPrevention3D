package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimBlockAbandonmentTest
{
    @Test
    void appliesBukkitCeilingSemanticsForPartialReturns()
    {
        assertEquals(13, ClaimBlockAbandonment.accruedPenalty(25, 0.5D));
        assertEquals(87, ClaimBlockAbandonment.accruedAfterAbandonment(100, 25, 0.5D));
    }

    @Test
    void preservesUnclampedUpstreamRatioBehavior()
    {
        assertEquals(0, ClaimBlockAbandonment.accruedPenalty(25, 1.0D));
        assertEquals(-25, ClaimBlockAbandonment.accruedPenalty(25, 2.0D));
        assertEquals(125, ClaimBlockAbandonment.accruedAfterAbandonment(100, 25, 2.0D));
    }
}
