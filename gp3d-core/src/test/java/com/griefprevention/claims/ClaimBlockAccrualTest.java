package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimBlockAccrualTest
{
    @Test
    void truncatesEachTenMinuteDeliveryLikeTheBukkitEvent()
    {
        assertEquals(16, ClaimBlockAccrual.blocksForDelivery(100, false, 0));
        assertEquals(0, ClaimBlockAccrual.blocksForDelivery(5, false, 0));
        assertEquals(4, ClaimBlockAccrual.blocksForDelivery(100, true, 25));
        assertEquals(25, ClaimBlockAccrual.blocksForDelivery(100, true, 150));
        assertEquals(0, ClaimBlockAccrual.blocksForDelivery(100, true, 0));
        assertEquals(0, ClaimBlockAccrual.blocksForDelivery(-100, false, 0));
    }

    @Test
    void appliesTheCapOnlyWhenPendingBlocksArePositiveAndAccruedIsBelowIt()
    {
        assertEquals(80000, ClaimBlockAccrual.materializeAccruedBlocks(79995, 16, 80000));
        assertEquals(90000, ClaimBlockAccrual.materializeAccruedBlocks(90000, 16, 80000));
        assertEquals(100, ClaimBlockAccrual.materializeAccruedBlocks(100, -1, 80000));
    }

    @Test
    void preservesUpstreamIntegerOverflow()
    {
        int overflowed = Integer.MAX_VALUE - 5 + 16;
        assertEquals(
                overflowed,
                ClaimBlockAccrual.materializeAccruedBlocks(
                        Integer.MAX_VALUE - 5,
                        16,
                        Integer.MAX_VALUE
                )
        );
        assertEquals(
                Integer.MIN_VALUE,
                ClaimBlockAccrual.addPendingBlocks(Integer.MAX_VALUE, 1)
        );
        assertEquals(-1794967296, ClaimBlockAccrual.idleThresholdSquared(50000));
    }
}
